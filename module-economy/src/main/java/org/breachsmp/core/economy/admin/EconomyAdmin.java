package org.breachsmp.core.economy.admin;

import org.breachsmp.core.api.audit.AuditEntry;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.EntryView;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.util.IdemKeys;
import org.breachsmp.core.economy.ledger.AccountDao;
import org.breachsmp.core.economy.ledger.HistoryRow;
import org.breachsmp.core.economy.ledger.JdbcLedgerService;
import org.breachsmp.core.economy.ledger.LedgerDao;
import org.breachsmp.core.economy.ledger.StoredTx;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Operazioni dello staff sull'economia (spec §14, E20): give, take, rollback compensativo, freeze, storico, massa monetaria.
 * Ogni operazione è un solo task sul pool DB che usa solo metodi bloccanti (D-18); l'audit si scrive dopo il commit.
 */
public final class EconomyAdmin {

    public static final int HISTORY_PAGE_SIZE = 10;
    public static final int MAX_REASON_LENGTH = 200;

    private static final AccountRef FAUCET = AccountRef.system(SystemAccount.FAUCET);
    private static final AccountRef SINK = AccountRef.system(SystemAccount.SINK);

    private final DataSource dataSource;
    private final Executor db;
    private final SeasonService seasons;
    private final AccountDao accounts;
    private final JdbcLedgerService ledger;
    private final AuditService audit;
    private final Clock clock;
    private final Logger logger;

    public EconomyAdmin(DataSource dataSource, Executor db, SeasonService seasons, AccountDao accounts,
                        JdbcLedgerService ledger, AuditService audit, Clock clock, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.db = Objects.requireNonNull(db, "db");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** {@code SYS_FAUCET → giocatore}. */
    public CompletableFuture<TxResult> give(UUID actor, PlayerProfile target, long amount, String reason) {
        return transfer(actor, target, amount, reason, TxKinds.ADMIN_GIVE, "ECO_GIVE",
                List.of(new Leg(FAUCET, -amount), new Leg(AccountRef.player(target.uuid()), amount)));
    }

    /** {@code giocatore → SYS_SINK}; fallisce se il saldo non basta (niente prelievi parziali, E20). */
    public CompletableFuture<TxResult> take(UUID actor, PlayerProfile target, long amount, String reason) {
        return transfer(actor, target, amount, reason, TxKinds.ADMIN_TAKE, "ECO_TAKE",
                List.of(new Leg(AccountRef.player(target.uuid()), -amount), new Leg(SINK, amount)));
    }

    /** Transazione compensativa con gambe invertite (D-25); un secondo rollback della stessa transazione restituisce il primo. */
    public CompletableFuture<TxResult> rollback(UUID actor, long txId, String reason) {
        checkReason(reason);
        return CompletableFuture.supplyAsync(() -> {
            StoredTx original = readTx(txId).orElseThrow(() ->
                    new RollbackRefusedException("Transazione #" + txId + " non trovata."));
            int season = seasons.currentSeasonId();
            if (original.seasonId() != season) {
                throw new RollbackRefusedException("La transazione #" + txId + " è della stagione " + original.seasonId()
                        + ": si annullano solo quelle della stagione corrente.");
            }
            if (original.kind().equals(TxKinds.ROLLBACK)) {
                throw new RollbackRefusedException("La transazione #" + txId + " è già un rollback e non si annulla.");
            }
            List<Leg> legs = inverted(original.entries());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("of", txId);
            meta.put("reason", reason);
            TxResult result = ledger.executeNow(new TxRequest(IdemKeys.of("rollback:" + txId), TxKinds.ROLLBACK, legs, meta, actor));
            if (!result.replayed()) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("rollback_tx", result.txId());
                detail.put("reason", reason);
                auditQuietly(new AuditEntry(actor, "TX_ROLLBACK", "tx:" + txId, detail));
            }
            return result;
        }, db);
    }

    /** @return {@code true} se il conto è stato congelato ora, {@code false} se lo era già */
    public CompletableFuture<Boolean> freeze(UUID actor, PlayerProfile target, String reason) {
        return setFrozen(actor, target, reason, true);
    }

    /** @return {@code true} se il conto è stato sbloccato ora, {@code false} se non era congelato */
    public CompletableFuture<Boolean> unfreeze(UUID actor, PlayerProfile target, String reason) {
        return setFrozen(actor, target, reason, false);
    }

    /** Movimenti del giocatore nella stagione corrente, dal più recente; pagine da {@value #HISTORY_PAGE_SIZE}. */
    public CompletableFuture<List<HistoryRow>> history(UUID player, int page) {
        if (page < 1) {
            throw new IllegalArgumentException("Pagina non valida: " + page);
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<AccountDao.Snapshot> account = accounts.read(seasons.currentSeasonId(), AccountRef.player(player));
            if (account.isEmpty()) {
                return List.of();
            }
            try (Connection c = dataSource.getConnection()) {
                return LedgerDao.history(c, account.get().id(), HISTORY_PAGE_SIZE, (page - 1) * HISTORY_PAGE_SIZE);
            } catch (SQLException e) {
                throw new DataAccessException("Impossibile leggere lo storico: " + e.getMessage(), e);
            }
        }, db);
    }

    /** Massa monetaria = {@code -saldo(FAUCET) - saldo(SINK)} (spec §5). Bloccante (thread DB). */
    public long moneySupplyNow() {
        int season = seasons.currentSeasonId();
        long faucet = accounts.read(season, FAUCET).map(AccountDao.Snapshot::balance).orElse(0L);
        long sink = accounts.read(season, SINK).map(AccountDao.Snapshot::balance).orElse(0L);
        return -faucet - sink;
    }

    private CompletableFuture<TxResult> transfer(UUID actor, PlayerProfile target, long amount, String reason, String kind,
                                                 String action, List<Leg> legs) {
        checkReason(reason);
        if (amount <= 0) {
            throw new IllegalArgumentException("L'importo deve essere maggiore di zero");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reason", reason);
        meta.put("target", target.name());
        TxRequest request = new TxRequest(UUID.randomUUID(), kind, legs, meta, actor);
        return CompletableFuture.supplyAsync(() -> {
            TxResult result = ledger.executeNow(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("amount", amount);
            detail.put("reason", reason);
            detail.put("tx", result.txId());
            auditQuietly(new AuditEntry(actor, action, "player:" + target.uuid(), detail));
            return result;
        }, db);
    }

    private CompletableFuture<Boolean> setFrozen(UUID actor, PlayerProfile target, String reason, boolean frozen) {
        checkReason(reason);
        return CompletableFuture.supplyAsync(() -> {
            long accountId = accounts.ensure(seasons.currentSeasonId(), AccountRef.player(target.uuid()));
            boolean changed = accounts.setFrozen(accountId, frozen, clock.instant());
            if (changed) {
                auditQuietly(new AuditEntry(actor, frozen ? "ACCOUNT_FREEZE" : "ACCOUNT_UNFREEZE",
                        "player:" + target.uuid(), Map.of("reason", reason)));
            }
            return changed;
        }, db);
    }

    private Optional<StoredTx> readTx(long txId) {
        try (Connection c = dataSource.getConnection()) {
            return LedgerDao.findById(c, txId);
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile leggere la transazione #" + txId + ": " + e.getMessage(), e);
        }
    }

    /** L'operazione è già committata: un audit fallito si registra ma non annulla nulla (D-18). */
    private void auditQuietly(AuditEntry entry) {
        try {
            audit.recordNow(entry);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Audit non scritto per un'operazione già eseguita: " + entry, e);
        }
    }

    private static void checkReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Specifica un motivo.");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Motivo troppo lungo (massimo " + MAX_REASON_LENGTH + " caratteri).");
        }
    }

    private static List<Leg> inverted(List<EntryView> entries) {
        return entries.stream().map(entry -> new Leg(entry.account(), -entry.delta())).toList();
    }
}
