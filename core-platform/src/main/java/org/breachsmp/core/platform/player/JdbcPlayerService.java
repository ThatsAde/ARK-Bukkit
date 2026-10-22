package org.breachsmp.core.platform.player;

import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.api.util.Uuids;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Profili dei giocatori sulla tabella globale {@code player}. */
public final class JdbcPlayerService implements PlayerService {

    private static final String UPSERT = """
            INSERT INTO player (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)""";
    private static final String BY_NAME =
            "SELECT uuid, name, first_seen, last_seen FROM player WHERE name = ? ORDER BY last_seen DESC LIMIT 1";
    private static final String BY_UUID = "SELECT uuid, name, first_seen, last_seen FROM player WHERE uuid = ?";

    private final DataSource dataSource;
    private final Executor db;
    private final Clock clock;

    public JdbcPlayerService(DataSource dataSource, Executor db, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.db = Objects.requireNonNull(db, "db");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Crea o aggiorna il profilo (nome e ultimo accesso). Bloccante: si chiama dal thread asincrono di login. */
    public void upsert(UUID uuid, String name) {
        Timestamp now = Timestamp.from(clock.instant());
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(UPSERT)) {
            ps.setBytes(1, Uuids.toBytes(uuid));
            ps.setString(2, name);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile salvare il profilo di " + name + ": " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findByName(String name) {
        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> query(BY_NAME, ps -> ps.setString(1, name.trim())), db);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> query(BY_UUID, ps -> ps.setBytes(1, Uuids.toBytes(uuid))), db);
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private Optional<PlayerProfile> query(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerProfile(Uuids.fromBytes(rs.getBytes(1)), rs.getString(2),
                        rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant()));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile leggere il profilo del giocatore: " + e.getMessage(), e);
        }
    }
}
