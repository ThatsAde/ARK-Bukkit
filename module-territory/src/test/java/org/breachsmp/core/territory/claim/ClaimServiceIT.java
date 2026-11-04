package org.breachsmp.core.territory.claim;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.domain.territory.TerritoryConfigParser;
import org.breachsmp.core.domain.territory.TierTable;
import org.breachsmp.core.platform.audit.JdbcAuditService;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimServiceIT {

    private static final UUID W = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private HikariDataSource dataSource;
    private ExecutorService db;
    private ExecutorService compute;
    private CoreExecutors executors;
    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 25, 12, 0));
    private final SeasonService seasons = () -> 1;
    private final TierTable tiers = TerritoryConfigParser.parse(config()).tiers();
    private ClaimService claims;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        db = Executors.newFixedThreadPool(4);
        compute = Executors.newFixedThreadPool(4);
        executors = new CoreExecutors() {
            @Override
            public Executor main() {
                return Runnable::run;
            }

            @Override
            public Executor db() {
                return db;
            }

            @Override
            public Executor compute() {
                return compute;
            }

            @Override
            public Executor market() {
                return Runnable::run;
            }
        };
        claims = newService();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        db.shutdown();
        compute.shutdown();
        db.awaitTermination(5, TimeUnit.SECONDS);
        compute.awaitTermination(5, TimeUnit.SECONDS);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void createPersistsTheCreatorAsMemberAndProtects() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        assertTrue(claim.id() > 0);
        assertTrue(claims.isAuthorized(claim.id(), ALICE));
        assertFalse(claims.isAuthorized(claim.id(), BOB));
        assertEquals(claim.id(), claims.claimAt(W, 10, 70, -10).orElseThrow().id());
        assertEquals(1, count("SELECT COUNT(*) FROM claim_member"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLAIM_CREATE'"));
    }

    @Test
    void reservationBlocksASecondOverlappingPlacement() {
        CompletableFuture<LiveClaim> first = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16));  // non attendere
        assertEquals(Optional.of(PlacementRefusal.OVERLAPS_FOREIGN_CLAIM),
                claims.refusePlacement(BOB, new ClaimBox(W, 20, 64, 0, 16), 0, 50, Set.of(W)));
        first.join();
    }

    @Test
    void overlapIsAllowedWhereThePlacerIsAuthorized() throws Exception {
        claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        assertEquals(Optional.empty(), claims.refusePlacement(ALICE, new ClaimBox(W, 20, 64, 0, 16), 0, 50, Set.of(W)));
        assertEquals(Optional.of(PlacementRefusal.WORLD_DISABLED),
                claims.refusePlacement(ALICE, new ClaimBox(OTHER, 0, 64, 0, 16), 0, 50, Set.of(W)));
        assertEquals(Optional.of(PlacementRefusal.LIMIT_REACHED),
                claims.refusePlacement(ALICE, new ClaimBox(W, 500, 64, 0, 16), 0, 1, Set.of(W)));
    }

    @Test
    void failedInsertLeavesNoGhostClaim() throws SQLException {
        execute("INSERT INTO claim (season_id, world_uuid, cup_x, cup_y, cup_z, radius, created_by, created_at) VALUES (1, "
                + hex(W) + ", 0, 64, 0, 16, " + hex(BOB) + ", NOW(3))");          // stessa posizione: UNIQUE violata
        assertThrows(ExecutionException.class, () -> claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS));
        assertEquals(Optional.empty(), claims.claimByCupboard(W, 0, 64, 0));
    }

    @Test
    void pinAuthorizesAndWrongPinsAreAudited() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        assertEquals(PinResult.NO_PIN_SET, claims.tryPin(claim.id(), BOB, "1234").get(5, SECONDS));
        claims.setPin(claim.id(), "4821", ALICE).get(5, SECONDS);
        assertEquals(PinResult.WRONG_PIN, claims.tryPin(claim.id(), BOB, "0000").get(5, SECONDS));
        assertEquals(PinResult.AUTHORIZED, claims.tryPin(claim.id(), BOB, "4821").get(5, SECONDS));
        assertEquals(PinResult.ALREADY_AUTHORIZED, claims.tryPin(claim.id(), BOB, "4821").get(5, SECONDS));
        assertTrue(claims.isAuthorized(claim.id(), BOB));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'TC_PIN_FAILED'"));
        assertEquals(null, queryString("SELECT pin_hash FROM claim WHERE pin_hash = '4821'"), "mai in chiaro");
    }

    @Test
    void pinAttemptsAreRateLimited() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        claims.setPin(claim.id(), "4821", ALICE).get(5, SECONDS);
        for (int i = 0; i < 5; i++) {
            claims.tryPin(claim.id(), BOB, "000" + i).get(5, SECONDS);
        }
        assertEquals(PinResult.RATE_LIMITED, claims.tryPin(claim.id(), BOB, "4821").get(5, SECONDS));
        assertEquals(PinResult.RATE_LIMITED, claims.tryPin(claim.id(), CAROL, "4821").get(5, SECONDS), "limite anche per claim");
        clock.advance(Duration.ofMinutes(11));
        assertEquals(PinResult.AUTHORIZED, claims.tryPin(claim.id(), BOB, "4821").get(5, SECONDS));
    }

    @Test
    void anyMemberCanRemoveAnyoneAndOnlyMembersChangeThePin() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        claims.setPin(claim.id(), "4821", ALICE).get(5, SECONDS);
        claims.tryPin(claim.id(), BOB, "4821").get(5, SECONDS);
        claims.unauthorize(claim.id(), ALICE, BOB).get(5, SECONDS);   // anche il creatore (T5)
        assertFalse(claims.isAuthorized(claim.id(), ALICE));
        ExecutionException error = assertThrows(ExecutionException.class, () -> claims.setPin(claim.id(), "9999", ALICE).get(5, SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void removeDeletesClaimAndMembersAndFreesTheArea() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        claims.remove(claim.id(), ALICE, "rotto").get(5, SECONDS);
        assertEquals(Optional.empty(), claims.claimAt(W, 0, 64, 0));
        assertEquals(0, count("SELECT COUNT(*) FROM claim"));
        assertEquals(0, count("SELECT COUNT(*) FROM claim_member"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLAIM_REMOVE'"));
    }

    @Test
    void aSecondRemoveWhileTheFirstIsInFlightIsRefused() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        CompletableFuture<Void> first = claims.remove(claim.id(), ALICE, "rotto");     // non attendere
        CompletableFuture<Void> second = claims.remove(claim.id(), BOB, "rotto");     // due rotture insieme: un solo item
        first.get(5, SECONDS);
        ExecutionException error = assertThrows(ExecutionException.class, () -> second.get(5, SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLAIM_REMOVE'"));
    }

    @Test
    void loadRestoresEveryClaimOfTheSeason() throws Exception {
        LiveClaim claim = claims.create(ALICE, new ClaimBox(W, 0, 64, 0, 16)).get(5, SECONDS);
        claims.setPin(claim.id(), "4821", ALICE).get(5, SECONDS);
        ClaimService fresh = newService();
        fresh.loadNow();
        assertEquals(claim.id(), fresh.claimAt(W, 5, 64, 5).orElseThrow().id());
        assertTrue(fresh.isAuthorized(claim.id(), ALICE));
        assertEquals(PinResult.AUTHORIZED, fresh.tryPin(claim.id(), BOB, "4821").get(5, SECONDS));
    }

    /** Un {@link ClaimService} nuovo sullo stesso DB (indice vuoto finché non si chiama {@code loadNow}). */
    private ClaimService newService() {
        JdbcAuditService audit = new JdbcAuditService(dataSource, db, clock);
        return new ClaimService(dataSource, executors, seasons, audit, tiers, () -> 5, clock, Logger.getLogger("test"));
    }

    private long count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static String hex(UUID uuid) {
        return "UNHEX('" + uuid.toString().replace("-", "") + "')";
    }

    /** Config minima del territorio, nella forma che SnakeYAML produce da {@code territory.yml}. */
    private static Map<String, Object> config() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("config_version", 1);
        root.put("worlds", List.of("world"));
        root.put("default_tier", 1);
        Map<Object, Object> tierMap = new LinkedHashMap<>();
        tierMap.put(1, Map.of("name", "Legno", "hp", 10, "materials", List.of("OAK_PLANKS")));
        tierMap.put(2, Map.of("name", "Pietra", "hp", 30, "materials", List.of("COBBLESTONE")));
        root.put("tiers", tierMap);
        root.put("upgrades", Map.of("OAK_PLANKS", Map.of("to", "COBBLESTONE", "cost", Map.of("COBBLESTONE", 1))));
        root.put("cupboard", Map.of("material", "BARREL", "tier", 2,
                "recipe", Map.of("pattern", List.of("PPP", "PIP", "PPP"), "ingredients", Map.of("P", "OAK_PLANKS", "I", "IRON_INGOT"))));
        root.put("hammer", Map.of("material", "BRUSH",
                "recipe", Map.of("pattern", List.of(" I", "S "), "ingredients", Map.of("I", "IRON_INGOT", "S", "STICK"))));
        return root;
    }
}
