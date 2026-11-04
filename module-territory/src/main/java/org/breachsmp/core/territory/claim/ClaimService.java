package org.breachsmp.core.territory.claim;

import org.breachsmp.core.api.audit.AuditEntry;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.territory.ClaimView;
import org.breachsmp.core.api.territory.TerritoryService;
import org.breachsmp.core.domain.ratelimit.SlidingWindowLimiter;
import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.domain.territory.ClaimIndex;
import org.breachsmp.core.domain.territory.PinHash;
import org.breachsmp.core.domain.territory.PinHasher;
import org.breachsmp.core.domain.territory.TierTable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Claim dei Tool Cupboard (spec §6, design M2 T2–T7): stato nel DB, indice in memoria per i listener. Ogni modifica va prima
 * nel DB (pool DB) e poi nell'indice, sul main thread (D-36); le letture ({@link #claimCovering}, {@link #claimAt},
 * {@link #refusePlacement}) sono solo in memoria e si fanno dal main thread.
 */
public final class ClaimService implements TerritoryService {

    private static final Duration PIN_WINDOW = Duration.ofMinutes(10);

    private record CupboardKey(UUID world, int x, int y, int z) {
        static CupboardKey of(ClaimBox box) {
            return new CupboardKey(box.world(), box.x(), box.y(), box.z());
        }
    }

    private final DataSource dataSource;
    private final CoreExecutors executors;
    private final SeasonService seasons;
    private final AuditService audit;
    private final TierTable tiers;
    private final LongSupplier pinAttemptsPer10Min;
    private final Clock clock;
    private final Logger logger;
    private final ClaimDao dao = new ClaimDao();
    private final PinHasher hasher = new PinHasher();
    private final SlidingWindowLimiter pinByPlayer;
    private final SlidingWindowLimiter pinByClaim;

    private final ClaimIndex<LiveClaim> index = new ClaimIndex<>();
    private final Map<Long, LiveClaim> byId = new ConcurrentHashMap<>();
    private final Map<CupboardKey, LiveClaim> byCupboard = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> createdBy = new ConcurrentHashMap<>();

    public ClaimService(DataSource dataSource, CoreExecutors executors, SeasonService seasons, AuditService audit,
                        TierTable tiers, LongSupplier pinAttemptsPer10Min, Clock clock, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.tiers = Objects.requireNonNull(tiers, "tiers");
        this.pinAttemptsPer10Min = Objects.requireNonNull(pinAttemptsPer10Min, "pinAttemptsPer10Min");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pinByPlayer = new SlidingWindowLimiter(clock, PIN_WINDOW);
        this.pinByClaim = new SlidingWindowLimiter(clock, PIN_WINDOW);
    }

    /**
     * Carica nell'indice tutti i claim della stagione, bloccando: solo in {@code onEnable} (eccezione I2). I claim proteggono
     * anche nei chunk non caricati, perché l'indice non dipende dai chunk (Review Focus 5).
     */
    public void loadNow() {
        if (index.size() > 0) {
            throw new IllegalStateException("Claim già caricati");
        }
        List<ClaimDao.Row> rows;
        try (Connection c = dataSource.getConnection()) {
            rows = dao.loadSeason(c, seasons.currentSeasonId());
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile caricare i claim: " + e.getMessage(), e);
        }
        for (ClaimDao.Row row : rows) {
            LiveClaim claim = new LiveClaim(row.id(), row.box(), row.createdBy(), row.createdAt(), row.members(), row.pin(), false);
            reserve(claim);
            byId.put(claim.id(), claim);
        }
        logger.info("Territorio: " + rows.size() + " claim caricati");
    }

    /** Claim che copre il blocco (il più vicino al cupboard), anche se solo riservato. Main thread. */
    public Optional<LiveClaim> claimCovering(UUID world, int x, int y, int z) {
        List<LiveClaim> covering = index.covering(world, x, y, z);
        return covering.isEmpty() ? Optional.empty() : Optional.of(covering.getFirst());
    }

    /** Claim del cupboard in quella posizione, anche se solo riservato. */
    public Optional<LiveClaim> claimByCupboard(UUID world, int x, int y, int z) {
        return Optional.ofNullable(byCupboard.get(new CupboardKey(world, x, y, z)));
    }

    /**
     * Perché il giocatore non può piazzare un cupboard che apre {@code box}; vuoto se può. Le riserve contano come claim
     * (Review Focus 1). Main thread.
     */
    public Optional<PlacementRefusal> refusePlacement(UUID player, ClaimBox box, int gap, long maxPerPlayer, Set<UUID> worlds) {
        if (!worlds.contains(box.world())) {
            return Optional.of(PlacementRefusal.WORLD_DISABLED);
        }
        if (byCupboard.containsKey(CupboardKey.of(box))) {
            return Optional.of(PlacementRefusal.POSITION_TAKEN);
        }
        if (createdBy.getOrDefault(player, 0) >= maxPerPlayer) {
            return Optional.of(PlacementRefusal.LIMIT_REACHED);
        }
        for (LiveClaim other : index.overlapping(box, gap)) {
            if (!other.members().contains(player)) {
                return Optional.of(PlacementRefusal.OVERLAPS_FOREIGN_CLAIM);
            }
        }
        return Optional.empty();
    }

    /**
     * Riserva subito il claim nell'indice (D-36), poi lo scrive nel DB con il creatore come autorizzato. Se la scrittura
     * fallisce la riserva sparisce e il future fallisce: chi chiama restituisce il cupboard. Main thread.
     */
    public CompletableFuture<LiveClaim> create(UUID creator, ClaimBox box) {
        Objects.requireNonNull(creator, "creator");
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        LiveClaim reserved = new LiveClaim(0, box, creator, now, Set.of(creator), null, true);
        reserve(reserved);
        return CompletableFuture.supplyAsync(() -> insertNow(reserved), executors.db())
                .handleAsync((id, error) -> {
                    if (error != null) {
                        unreserve(reserved);
                        throw error instanceof CompletionException completion ? completion : new CompletionException(error);
                    }
                    reserved.confirm(id);
                    byId.put(id, reserved);
                    return reserved;
                }, executors.main());
    }

    /** Cancella il claim e i suoi autorizzati (D-35), poi libera l'area. {@code actor} {@code null} = console. */
    public CompletableFuture<Void> remove(long claimId, UUID actor, String reason) {
        LiveClaim claim = byId.get(claimId);
        if (claim == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Claim #" + claimId + " inesistente"));
        }
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                dao.delete(c, claimId);
            } catch (SQLException e) {
                throw new DataAccessException("Impossibile rimuovere il claim #" + claimId + ": " + e.getMessage(), e);
            }
            Map<String, Object> detail = boxDetail(claim.box());
            detail.put("reason", reason == null ? "" : reason);
            auditQuietly(new AuditEntry(actor, "CLAIM_REMOVE", subject(claimId), detail));
        }, executors.db()).thenRunAsync(() -> {
            unreserve(claim);
            byId.remove(claimId);
        }, executors.main());
    }

    /** Imposta o cambia il PIN; solo un autorizzato può farlo ({@link IllegalStateException} altrimenti). Main thread. */
    public CompletableFuture<Void> setPin(long claimId, String pin, UUID actor) {
        LiveClaim claim = byId.get(claimId);
        if (claim == null || !claim.members().contains(actor)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Solo un autorizzato può cambiare il PIN."));
        }
        try {
            PinHasher.validate(pin);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(() -> hasher.hash(pin), executors.compute())
                .thenApplyAsync(hash -> {
                    try (Connection c = dataSource.getConnection()) {
                        dao.setPin(c, claimId, hash);
                    } catch (SQLException e) {
                        throw new DataAccessException("Impossibile salvare il PIN del claim #" + claimId + ": " + e.getMessage(), e);
                    }
                    auditQuietly(new AuditEntry(actor, "TC_PIN_SET", subject(claimId), Map.of()));
                    return hash;
                }, executors.db())
                .thenAcceptAsync(claim::pin, executors.main());
    }

    /**
     * Prova il PIN: se è giusto il giocatore diventa autorizzato. I tentativi sono limitati per giocatore e per claim
     * ({@code territory.pin_attempts_per_10_min}); ogni errore va in audit come {@code TC_PIN_FAILED} (D-37). Main thread.
     */
    public CompletableFuture<PinResult> tryPin(long claimId, UUID player, String pin) {
        LiveClaim claim = byId.get(claimId);
        if (claim == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Claim #" + claimId + " inesistente"));
        }
        if (claim.members().contains(player)) {
            return CompletableFuture.completedFuture(PinResult.ALREADY_AUTHORIZED);
        }
        Optional<PinHash> stored = claim.pin();
        if (stored.isEmpty()) {
            return CompletableFuture.completedFuture(PinResult.NO_PIN_SET);
        }
        long limit = pinAttemptsPer10Min.getAsLong();
        if (!pinByPlayer.tryAcquire(player, limit) || !pinByClaim.tryAcquire(claimKey(claimId), limit)) {
            return CompletableFuture.completedFuture(PinResult.RATE_LIMITED);
        }
        PinHash hash = stored.get();
        return CompletableFuture.supplyAsync(() -> hasher.verify(pin, hash), executors.compute())
                .thenApplyAsync(correct -> {
                    if (!correct) {
                        auditQuietly(new AuditEntry(player, "TC_PIN_FAILED", subject(claimId), Map.of()));
                        return PinResult.WRONG_PIN;
                    }
                    try (Connection c = dataSource.getConnection()) {
                        dao.addMember(c, claimId, player, clock.instant(), player);
                    } catch (SQLException e) {
                        throw new DataAccessException("Impossibile autorizzare sul claim #" + claimId + ": " + e.getMessage(), e);
                    }
                    auditQuietly(new AuditEntry(player, "TC_AUTHORIZE", subject(claimId), Map.of("via", "pin")));
                    return PinResult.AUTHORIZED;
                }, executors.db())
                .thenApplyAsync(result -> {
                    if (result == PinResult.AUTHORIZED) {
                        claim.addMember(player);
                    }
                    return result;
                }, executors.main());
    }

    /** Toglie un autorizzato; qualsiasi autorizzato può togliere chiunque, anche il creatore (T5). Main thread. */
    public CompletableFuture<Void> unauthorize(long claimId, UUID target, UUID actor) {
        LiveClaim claim = byId.get(claimId);
        if (claim == null || !claim.members().contains(actor)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Solo un autorizzato può togliere autorizzazioni."));
        }
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                dao.removeMember(c, claimId, target);
            } catch (SQLException e) {
                throw new DataAccessException("Impossibile togliere l'autorizzazione sul claim #" + claimId + ": "
                        + e.getMessage(), e);
            }
            auditQuietly(new AuditEntry(actor, "TC_UNAUTHORIZE", subject(claimId), Map.of("target", target.toString())));
        }, executors.db()).thenRunAsync(() -> claim.removeMember(target), executors.main());
    }

    /** Claim nell'indice, riserve comprese. */
    public int activeCount() {
        return index.size();
    }

    @Override
    public Optional<ClaimView> claimAt(UUID world, int x, int y, int z) {
        for (LiveClaim claim : index.covering(world, x, y, z)) {
            if (!claim.pending()) {
                return Optional.of(claim.view());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isAuthorized(long claimId, UUID player) {
        LiveClaim claim = byId.get(claimId);
        return claim != null && claim.members().contains(player);
    }

    @Override
    public int tierOf(String materialKey) {
        return tiers.tierOf(materialKey);
    }

    @Override
    public int hpOfTier(int tier) {
        return tiers.hp(tier);
    }

    private long insertNow(LiveClaim claim) {
        long id;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                id = dao.insert(c, seasons.currentSeasonId(), claim.box(), claim.createdBy(), claim.createdAt());
                dao.addMember(c, id, claim.createdBy(), claim.createdAt(), null);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile creare il claim in " + claim.box() + ": " + e.getMessage(), e);
        }
        auditQuietly(new AuditEntry(claim.createdBy(), "CLAIM_CREATE", subject(id), boxDetail(claim.box())));
        return id;
    }

    private void reserve(LiveClaim claim) {
        index.add(claim, claim.box());
        byCupboard.put(CupboardKey.of(claim.box()), claim);
        createdBy.merge(claim.createdBy(), 1, Integer::sum);
    }

    private void unreserve(LiveClaim claim) {
        index.remove(claim);
        byCupboard.remove(CupboardKey.of(claim.box()), claim);
        createdBy.computeIfPresent(claim.createdBy(), (player, count) -> count > 1 ? count - 1 : null);
    }

    /** Audit dopo il commit (D-18): se fallisce l'operazione resta valida, il DB è la traccia autorevole. */
    private void auditQuietly(AuditEntry entry) {
        try {
            audit.recordNow(entry);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Audit non scritto per un'operazione già eseguita: " + entry, e);
        }
    }

    private static Map<String, Object> boxDetail(ClaimBox box) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("world", box.world().toString());
        detail.put("x", box.x());
        detail.put("y", box.y());
        detail.put("z", box.z());
        detail.put("radius", box.radius());
        return detail;
    }

    private static String subject(long claimId) {
        return "claim:" + claimId;
    }

    private static UUID claimKey(long claimId) {
        return new UUID(0L, claimId);
    }
}
