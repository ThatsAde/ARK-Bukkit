package org.breachsmp.core.economy;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.util.Uuids;
import org.breachsmp.core.economy.ledger.AccountDao;
import org.breachsmp.core.economy.ledger.BalanceCache;
import org.breachsmp.core.economy.ledger.JdbcLedgerService;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.breachsmp.core.platform.testkit.TestDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Ledger reale su MariaDB di test (stagione 1) per gli IT di {@code module-economy}. */
public final class LedgerFixture implements AutoCloseable {

    public final HikariDataSource dataSource = TestDatabase.migrated();
    public final ExecutorService db = Executors.newFixedThreadPool(8);
    public final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 22, 12, 0));
    public final SeasonService seasons = () -> 1;
    public final AccountDao accounts = new AccountDao(dataSource);
    public final BalanceCache cache = new BalanceCache();
    public final JdbcLedgerService ledger =
            new JdbcLedgerService(dataSource, db, seasons, accounts, cache, clock, Logger.getLogger("test"));

    /** {@code ADMIN_GIVE} da FAUCET al giocatore, atteso. */
    public TxResult give(UUID player, long units) {
        try {
            return ledger.execute(tx(TxKinds.ADMIN_GIVE, new Leg(AccountRef.system(SystemAccount.FAUCET), -units),
                    new Leg(AccountRef.player(player), units))).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Richiesta con chiave casuale, meta vuota, actor nullo. */
    public TxRequest tx(String kind, Leg... legs) {
        return new TxRequest(UUID.randomUUID(), kind, List.of(legs), Map.of(), null);
    }

    /** Saldo nel DB (0 se il conto non esiste). */
    public long dbBalance(AccountRef ref) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT balance FROM account WHERE season_id = 1 AND owner_type = ? AND owner_ref = ?")) {
            ps.setString(1, ref.type().name());
            ps.setBytes(2, Uuids.toBytes(ref.owner()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public long count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public String queryString(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    public byte[] queryBytes(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getBytes(1) : null;
        }
    }

    public void execute(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    @Override
    public void close() {
        db.shutdownNow();
        dataSource.close();
    }
}
