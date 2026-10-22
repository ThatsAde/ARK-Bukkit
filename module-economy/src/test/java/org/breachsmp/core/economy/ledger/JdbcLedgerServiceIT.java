package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.api.economy.AccountFrozenException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.IdempotencyConflictException;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.InvalidTransactionException;
import org.breachsmp.core.api.economy.LedgerTxCommitted;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.util.Uuids;
import org.breachsmp.core.economy.LedgerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcLedgerServiceIT {

    private static final AccountRef FAUCET = AccountRef.system(SystemAccount.FAUCET);
    private static final AccountRef SINK = AccountRef.system(SystemAccount.SINK);

    private LedgerFixture f;

    @BeforeEach
    void setUp() {
        f = new LedgerFixture();
    }

    @AfterEach
    void tearDown() {
        if (f != null) {
            f.close();
        }
    }

    @Test
    void giveThenPayMovesMoneyAndRecordsEntries() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        f.give(alice, 1_000);
        TxResult pay = f.ledger.execute(f.tx(TxKinds.PAY, new Leg(AccountRef.player(alice), -300),
                new Leg(AccountRef.player(bob), 300))).get(5, SECONDS);
        assertFalse(pay.replayed());
        assertEquals(OptionalLong.of(700), pay.balanceAfter(AccountRef.player(alice)));
        assertEquals(OptionalLong.of(300), pay.balanceAfter(AccountRef.player(bob)));
        assertEquals(700, f.dbBalance(AccountRef.player(alice)));
        assertEquals(-1_000, f.dbBalance(FAUCET));
        assertEquals(2, f.count("SELECT COUNT(*) FROM ledger_tx"));
        assertEquals(0, f.count("SELECT COUNT(*) FROM (SELECT tx_id FROM ledger_entry GROUP BY tx_id HAVING SUM(delta) <> 0) x"));
        assertEquals(f.clock.instant(), pay.createdAt());
    }

    @Test
    void insufficientFundsLeavesNoTrace() throws Exception {
        UUID alice = UUID.randomUUID();
        f.give(alice, 100);
        ExecutionException error = assertThrows(ExecutionException.class, () -> f.ledger.execute(
                f.tx(TxKinds.PAY, new Leg(AccountRef.player(alice), -101), new Leg(AccountRef.player(UUID.randomUUID()), 101)))
                .get(5, SECONDS));
        assertEquals(AccountRef.player(alice), assertInstanceOf(InsufficientFundsException.class, error.getCause()).account());
        assertEquals(1, f.count("SELECT COUNT(*) FROM ledger_tx"));
        assertEquals(100, f.dbBalance(AccountRef.player(alice)));
    }

    @Test
    void sameIdemKeyIsExecutedOnce() throws Exception {
        UUID alice = UUID.randomUUID();
        TxRequest give = f.tx(TxKinds.ADMIN_GIVE, new Leg(FAUCET, -500), new Leg(AccountRef.player(alice), 500));
        TxResult first = f.ledger.execute(give).get(5, SECONDS);
        TxResult second = f.ledger.execute(give).get(5, SECONDS);
        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.txId(), second.txId());
        assertEquals(first.entries(), second.entries());
        assertEquals(500, f.dbBalance(AccountRef.player(alice)));
        assertEquals(1, f.count("SELECT COUNT(*) FROM ledger_tx"));
    }

    @Test
    void sameKeyWithDifferentKindIsAConflict() throws Exception {
        UUID key = UUID.randomUUID();
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        f.ledger.execute(new TxRequest(key, TxKinds.ADMIN_GIVE, List.of(new Leg(FAUCET, -5), new Leg(alice, 5)), Map.of(), null))
                .get(5, SECONDS);
        ExecutionException error = assertThrows(ExecutionException.class, () -> f.ledger.execute(
                new TxRequest(key, TxKinds.ADMIN_TAKE, List.of(new Leg(alice, -5), new Leg(SINK, 5)), Map.of(), null)).get(5, SECONDS));
        assertInstanceOf(IdempotencyConflictException.class, error.getCause());
        assertEquals(5, f.dbBalance(alice));
    }

    @Test
    void frozenAccountBlocksPayButNotAdminKinds() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        f.give(alice, 1_000);
        f.give(bob, 1);
        f.accounts.setFrozen(f.accounts.ensure(1, AccountRef.player(bob)), true, f.clock.instant());
        ExecutionException toFrozen = assertThrows(ExecutionException.class, () -> f.ledger.execute(
                f.tx(TxKinds.PAY, new Leg(AccountRef.player(alice), -10), new Leg(AccountRef.player(bob), 10))).get(5, SECONDS));
        assertEquals(AccountRef.player(bob), assertInstanceOf(AccountFrozenException.class, toFrozen.getCause()).account());
        f.ledger.execute(f.tx(TxKinds.ADMIN_TAKE, new Leg(AccountRef.player(bob), -1), new Leg(SINK, 1))).get(5, SECONDS);
        assertEquals(0, f.dbBalance(AccountRef.player(bob)));
        assertEquals(1_000, f.dbBalance(AccountRef.player(alice)));
    }

    @Test
    void sinkCannotGoNegative() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> f.ledger.execute(
                f.tx(TxKinds.ROLLBACK, new Leg(SINK, -1), new Leg(AccountRef.player(UUID.randomUUID()), 1))).get(5, SECONDS));
        assertEquals(SINK, assertInstanceOf(InsufficientFundsException.class, error.getCause()).account());
    }

    @Test
    void invalidRequestNeverTouchesTheDatabase() throws Exception {
        ExecutionException error = assertThrows(ExecutionException.class, () -> f.ledger.execute(
                f.tx(TxKinds.PAY, new Leg(AccountRef.player(UUID.randomUUID()), -1), new Leg(AccountRef.player(UUID.randomUUID()), 2)))
                .get(5, SECONDS));
        assertInstanceOf(InvalidTransactionException.class, error.getCause());
        assertEquals(0, f.count("SELECT COUNT(*) FROM account"));
    }

    @Test
    void commitPublishesEventAndUpdatesCacheFailureDoesNot() throws Exception {
        List<LedgerTxCommitted> events = new CopyOnWriteArrayList<>();
        f.ledger.subscribe(events::add);
        UUID alice = UUID.randomUUID();
        TxResult result = f.give(alice, 250);
        assertEquals(List.of(new LedgerTxCommitted(result)), events);
        assertEquals(OptionalLong.of(250), f.cache.get(AccountRef.player(alice)));
        assertEquals(250L, f.ledger.balance(AccountRef.player(alice)).get(5, SECONDS));
        assertThrows(ExecutionException.class, () -> f.ledger.execute(f.tx(TxKinds.PAY,
                new Leg(AccountRef.player(alice), -999), new Leg(AccountRef.player(UUID.randomUUID()), 999))).get(5, SECONDS));
        assertEquals(1, events.size());
        f.ledger.execute(f.tx(TxKinds.PAY, new Leg(AccountRef.player(alice), -50),
                new Leg(AccountRef.player(UUID.randomUUID()), 50))).get(5, SECONDS);
        assertEquals(OptionalLong.of(200), f.cache.get(AccountRef.player(alice)));
    }

    @Test
    void balanceOfUnknownAccountIsZero() throws Exception {
        assertEquals(0L, f.ledger.balance(AccountRef.player(UUID.randomUUID())).get(5, SECONDS));
    }

    @Test
    void metaAndActorAreStored() throws Exception {
        UUID admin = UUID.randomUUID();
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        f.ledger.execute(new TxRequest(UUID.randomUUID(), TxKinds.ADMIN_GIVE, List.of(new Leg(FAUCET, -1), new Leg(alice, 1)),
                Map.of("reason", "rimborso \"bug\""), admin)).get(5, SECONDS);
        assertEquals("rimborso \"bug\"", f.queryString("SELECT JSON_VALUE(meta, '$.reason') FROM ledger_tx"));
        assertEquals(admin, Uuids.fromBytes(f.queryBytes("SELECT actor FROM ledger_tx")));
    }

    @Test
    void cacheKeepsTheHighestVersion() {
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        f.cache.offer(alice, 5, 500);
        f.cache.offer(alice, 4, 400);
        assertEquals(OptionalLong.of(500), f.cache.get(alice));
        f.cache.offer(alice, 6, 600);
        assertEquals(OptionalLong.of(600), f.cache.get(alice));
    }
}
