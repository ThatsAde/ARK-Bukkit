package org.breachsmp.core.platform.tunable;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.tunable.Tunable;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TunableRegistryIT {

    private HikariDataSource dataSource;
    private ExecutorService db;
    private TunableRegistry registry;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        db = Executors.newFixedThreadPool(2);
        registry = new TunableRegistry(dataSource, db, new MutableClock(LocalDateTime.of(2026, 10, 22, 12, 0)),
                Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void setPersistsUpdatesValueAndWritesAuditInOneTransaction() throws Exception {
        Tunable fee = registry.registerLong("economy.transfer_fee_bp", 0, 0, 5000, "tassa");
        UUID admin = UUID.randomUUID();
        TunableChange change = registry.set("economy.transfer_fee_bp", " 250 ", admin).get(5, SECONDS);
        assertEquals(250, fee.get());
        assertEquals(0, change.previous());
        assertEquals(250, change.now().value());
        assertEquals("250", queryString("SELECT value FROM tunable WHERE tkey = 'economy.transfer_fee_bp'"));
        assertEquals("TUNABLE_SET", queryString("SELECT action FROM audit_log WHERE subject = 'economy.transfer_fee_bp'"));
        assertEquals("0", queryString("SELECT JSON_VALUE(detail, '$.old') FROM audit_log"));
        assertEquals("250", queryString("SELECT JSON_VALUE(detail, '$.new') FROM audit_log"));
    }

    @Test
    void invalidValuesAreRejectedWithoutWrites() throws SQLException {
        Tunable fee = registry.registerLong("economy.transfer_fee_bp", 0, 0, 5000, "tassa");
        for (String raw : List.of("abc", "5001", "-1", "1.5", "")) {
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> registry.set("economy.transfer_fee_bp", raw, null).get(5, SECONDS), raw);
            assertInstanceOf(IllegalArgumentException.class, error.getCause(), raw);
        }
        ExecutionException unknown = assertThrows(ExecutionException.class,
                () -> registry.set("nope", "1", null).get(5, SECONDS));
        assertEquals("Tunable sconosciuto: nope", unknown.getCause().getMessage());
        assertEquals(0, fee.get());
        assertEquals("0", queryString("SELECT COUNT(*) FROM tunable"));
        assertEquals("0", queryString("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void reloadReadsDbAndFallsBackToDefaultOnGarbage() throws Exception {
        Tunable rate = registry.registerLong("economy.pay_rate_per_minute", 10, 1, 1000, "rate");
        execute("INSERT INTO tunable (tkey, value, updated_at) VALUES ('economy.pay_rate_per_minute', '42', NOW(3))");
        registry.reload();
        assertEquals(42, rate.get());
        execute("UPDATE tunable SET value = 'garbage' WHERE tkey = 'economy.pay_rate_per_minute'");
        registry.reload();
        assertEquals(10, rate.get());
        execute("DELETE FROM tunable");
        registry.reload();
        assertEquals(10, rate.get());
    }

    private String queryString(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }
}
