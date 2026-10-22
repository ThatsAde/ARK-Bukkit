package org.breachsmp.core.economy.ledger;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountDaoIT {

    private HikariDataSource dataSource;
    private AccountDao dao;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        dao = new AccountDao(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void ensureCreatesOnceAndReturnsStableIds() throws SQLException {
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        AccountRef sink = AccountRef.system(SystemAccount.SINK);
        Map<AccountRef, Long> first = dao.ensure(1, List.of(alice, sink));
        Map<AccountRef, Long> again = new AccountDao(dataSource).ensure(1, List.of(alice, sink)); // senza cache
        assertEquals(first, again);
        assertEquals(2, count("SELECT COUNT(*) FROM account"));
        assertEquals(Optional.of(new AccountDao.Snapshot(first.get(alice), 0, 0, false)), dao.read(1, alice));
        assertEquals(Optional.empty(), dao.read(1, AccountRef.player(UUID.randomUUID())));
    }

    @Test
    void concurrentEnsureOfTheSameAccountCreatesOneRow() throws Exception {
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Long>> ids = pool.invokeAll(Collections.nCopies(16, () -> new AccountDao(dataSource).ensure(1, alice)));
            Set<Long> distinct = new HashSet<>();
            for (Future<Long> id : ids) {
                distinct.add(id.get());
            }
            assertEquals(1, distinct.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lockForUpdateReturnsRowsInIdOrderWithRefs() throws SQLException {
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        AccountRef faucet = AccountRef.system(SystemAccount.FAUCET);
        Map<AccountRef, Long> ids = dao.ensure(1, List.of(alice, faucet));
        List<Long> sorted = ids.values().stream().sorted().toList();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            List<LockedAccount> locked = dao.lockForUpdate(c, sorted);
            assertEquals(sorted, locked.stream().map(l -> l.state().id()).toList());
            assertEquals(Set.of(alice, faucet), locked.stream().map(l -> l.state().ref()).collect(Collectors.toSet()));
            c.rollback();
        }
    }

    @Test
    void setFrozenReportsWhetherItChanged() {
        long id = dao.ensure(1, AccountRef.player(UUID.randomUUID()));
        Instant at = Instant.parse("2026-10-22T10:00:00Z");
        assertTrue(dao.setFrozen(id, true, at));
        assertFalse(dao.setFrozen(id, true, at));
        assertTrue(dao.setFrozen(id, false, at));
        assertFalse(dao.setFrozen(id, false, at));
    }

    private long count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
