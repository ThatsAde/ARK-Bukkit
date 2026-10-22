package org.breachsmp.core.platform.audit;

import org.breachsmp.core.api.audit.AuditEntry;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.util.JsonText;
import org.breachsmp.core.api.util.Uuids;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Audit append-only su {@code audit_log} (spec §14). Il codice non fa mai UPDATE o DELETE su questa tabella. */
public final class JdbcAuditService implements AuditService {

    private static final String INSERT = "INSERT INTO audit_log (at, actor, action, subject, detail) VALUES (?, ?, ?, ?, ?)";

    private final DataSource dataSource;
    private final Executor db;
    private final Clock clock;

    public JdbcAuditService(DataSource dataSource, Executor db, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.db = Objects.requireNonNull(db, "db");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<Void> record(AuditEntry entry) {
        return CompletableFuture.runAsync(() -> recordNow(entry), db);
    }

    @Override
    public void recordNow(AuditEntry entry) {
        try (Connection c = dataSource.getConnection()) {
            insert(c, entry, clock.instant());
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile scrivere l'audit " + entry.action() + ": " + e.getMessage(), e);
        }
    }

    /** Scrive la riga sulla connessione data: chi chiama decide la transazione (es. stessa transazione del tunable, D-18). */
    public static void insert(Connection c, AuditEntry entry, Instant at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setTimestamp(1, Timestamp.from(at));
            if (entry.actor() == null) {
                ps.setNull(2, Types.BINARY);
            } else {
                ps.setBytes(2, Uuids.toBytes(entry.actor()));
            }
            ps.setString(3, entry.action());
            ps.setString(4, entry.subject());
            if (entry.detail().isEmpty()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, JsonText.write(entry.detail()));
            }
            ps.executeUpdate();
        }
    }
}
