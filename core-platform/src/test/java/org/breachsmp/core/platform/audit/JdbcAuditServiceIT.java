package org.breachsmp.core.platform.audit;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.audit.AuditEntry;
import org.breachsmp.core.api.util.Uuids;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAuditServiceIT {

    private HikariDataSource dataSource;
    private ExecutorService db;
    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 22, 12, 0));
    private JdbcAuditService audit;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        db = Executors.newFixedThreadPool(2);
        audit = new JdbcAuditService(dataSource, db, clock);
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
    void recordWritesActorActionSubjectAndJsonDetail() throws Exception {
        UUID admin = UUID.randomUUID();
        audit.record(new AuditEntry(admin, "ECO_GIVE", "player:abc", Map.of("amount", 500L))).get(5, SECONDS);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT actor, action, subject, JSON_VALUE(detail, '$.amount'), at FROM audit_log")) {
            assertTrue(rs.next());
            assertEquals(admin, Uuids.fromBytes(rs.getBytes(1)));
            assertEquals("ECO_GIVE", rs.getString(2));
            assertEquals("player:abc", rs.getString(3));
            assertEquals("500", rs.getString(4));
            assertEquals(clock.instant(), rs.getTimestamp(5).toInstant());
            assertFalse(rs.next());
        }
    }

    @Test
    void consoleActorAndEmptyDetailAreNull() throws SQLException {
        audit.recordNow(new AuditEntry(null, "TEST", null, null));
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT actor, subject, detail FROM audit_log")) {
            assertTrue(rs.next());
            assertNull(rs.getBytes(1));
            assertNull(rs.getString(2));
            assertNull(rs.getString(3));
        }
    }
}
