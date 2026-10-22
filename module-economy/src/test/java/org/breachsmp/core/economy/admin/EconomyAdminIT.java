package org.breachsmp.core.economy.admin;

import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.economy.LedgerFixture;
import org.breachsmp.core.economy.ledger.HistoryRow;
import org.breachsmp.core.platform.audit.JdbcAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyAdminIT {

    private LedgerFixture f;
    private EconomyAdmin economy;
    private final UUID staff = UUID.randomUUID();
    private final PlayerProfile bob = new PlayerProfile(UUID.randomUUID(), "Bob", Instant.EPOCH, Instant.EPOCH);

    @BeforeEach
    void setUp() {
        f = new LedgerFixture();
        JdbcAuditService audit = new JdbcAuditService(f.dataSource, f.db, f.clock);
        economy = new EconomyAdmin(f.dataSource, f.db, f.seasons, f.accounts, f.ledger, audit, f.clock, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        if (f != null) {
            f.close();
        }
    }

    @Test
    void giveAndTakeMoveMoneyAndWriteAudit() throws Exception {
        TxResult give = economy.give(staff, bob, 1_000, "evento").get(5, SECONDS);
        economy.take(staff, bob, 400, "correzione").get(5, SECONDS);
        assertEquals(600, f.dbBalance(AccountRef.player(bob.uuid())));
        assertEquals(600, economy.moneySupplyNow());
        assertEquals(2, f.count("SELECT COUNT(*) FROM audit_log WHERE action IN ('ECO_GIVE', 'ECO_TAKE')"));
        assertEquals(String.valueOf(give.txId()), f.queryString("SELECT JSON_VALUE(detail, '$.tx') FROM audit_log WHERE action = 'ECO_GIVE'"));
        assertEquals("evento", f.queryString("SELECT JSON_VALUE(meta, '$.reason') FROM ledger_tx WHERE id = " + give.txId()));
    }

    @Test
    void takeMoreThanBalanceFailsWithoutAudit() throws Exception {
        ExecutionException error = assertThrows(ExecutionException.class, () -> economy.take(staff, bob, 1, "x").get(5, SECONDS));
        assertInstanceOf(InsufficientFundsException.class, error.getCause());
        assertEquals(0, f.count("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void rollbackInvertsTheLegsOnceAndIsIdempotent() throws Exception {
        TxResult give = economy.give(staff, bob, 1_000, "errore").get(5, SECONDS);
        TxResult first = economy.rollback(staff, give.txId(), "dato per errore").get(5, SECONDS);
        TxResult second = economy.rollback(staff, give.txId(), "di nuovo").get(5, SECONDS);
        assertEquals(TxKinds.ROLLBACK, first.kind());
        assertTrue(second.replayed());
        assertEquals(first.txId(), second.txId());
        assertEquals(0, f.dbBalance(AccountRef.player(bob.uuid())));
        assertEquals(0, f.dbBalance(AccountRef.system(SystemAccount.FAUCET)));
        assertEquals(1, f.count("SELECT COUNT(*) FROM audit_log WHERE action = 'TX_ROLLBACK'"));
    }

    @Test
    void rollbackRefusals() throws Exception {
        TxResult give = economy.give(staff, bob, 1_000, "x").get(5, SECONDS);
        TxResult rollback = economy.rollback(staff, give.txId(), "y").get(5, SECONDS);
        assertRefused("è già un rollback", () -> economy.rollback(staff, rollback.txId(), "z"));
        assertRefused("Transazione #999999 non trovata", () -> economy.rollback(staff, 999_999, "z"));
        f.execute("INSERT INTO season (id, name, started_at) VALUES (2, 'Stagione 2', NOW(3))");
        f.execute("UPDATE ledger_tx SET season_id = 2 WHERE id = " + give.txId()); // solo nel test: simula una tx di un'altra stagione
        assertRefused("stagione corrente", () -> economy.rollback(staff, give.txId(), "z"));
    }

    @Test
    void rollbackThatWouldMakeAPlayerNegativeFails() throws Exception {
        TxResult give = economy.give(staff, bob, 1_000, "x").get(5, SECONDS);
        f.ledger.execute(f.tx(TxKinds.PAY, new Leg(AccountRef.player(bob.uuid()), -900),
                new Leg(AccountRef.player(UUID.randomUUID()), 900))).get(5, SECONDS);
        ExecutionException error = assertThrows(ExecutionException.class, () -> economy.rollback(staff, give.txId(), "z").get(5, SECONDS));
        assertInstanceOf(InsufficientFundsException.class, error.getCause());
        assertEquals(100, f.dbBalance(AccountRef.player(bob.uuid())));
    }

    @Test
    void freezeAndUnfreezeReportChangesAndAudit() throws Exception {
        assertTrue(economy.freeze(staff, bob, "indagine").get(5, SECONDS));
        assertFalse(economy.freeze(staff, bob, "di nuovo").get(5, SECONDS));
        assertTrue(economy.unfreeze(staff, bob, "chiarito").get(5, SECONDS));
        assertEquals(1, f.count("SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_FREEZE'"));
        assertEquals(1, f.count("SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_UNFREEZE'"));
    }

    @Test
    void historyIsNewestFirstAndPaged() throws Exception {
        for (int i = 1; i <= 12; i++) {
            economy.give(staff, bob, i, "n" + i).get(5, SECONDS);
        }
        List<HistoryRow> page1 = economy.history(bob.uuid(), 1).get(5, SECONDS);
        List<HistoryRow> page2 = economy.history(bob.uuid(), 2).get(5, SECONDS);
        assertEquals(10, page1.size());
        assertEquals(2, page2.size());
        assertEquals(12, page1.getFirst().delta());
        assertEquals(78, page1.getFirst().balanceAfter()); // 1 + 2 + … + 12
        assertEquals(1, page2.getLast().delta());
        assertEquals(List.of(), economy.history(UUID.randomUUID(), 1).get(5, SECONDS));
    }

    @Test
    void reasonIsRequiredAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> economy.give(staff, bob, 1, " "));
        assertThrows(IllegalArgumentException.class, () -> economy.give(staff, bob, 1, "x".repeat(201)));
    }

    private static void assertRefused(String fragment, Supplier<CompletableFuture<?>> action) {
        ExecutionException error = assertThrows(ExecutionException.class, () -> action.get().get(5, SECONDS));
        RollbackRefusedException refused = assertInstanceOf(RollbackRefusedException.class, error.getCause());
        assertTrue(refused.getMessage().contains(fragment), refused.getMessage());
    }
}
