package org.breachsmp.core.territory.claim;

import org.breachsmp.core.api.util.Uuids;
import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.domain.territory.PinHash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accesso alle tabelle {@code claim} e {@code claim_member} (V005). Ogni metodo lavora sulla connessione data: la
 * transazione la decide {@link ClaimService}, sempre sul pool DB (I2).
 */
public final class ClaimDao {

    /** Riga di {@code claim} con i suoi autorizzati, letta da {@link #loadSeason}. */
    public record Row(long id, ClaimBox box, UUID createdBy, Instant createdAt, PinHash pin, Set<UUID> members) {
    }

    private static final String INSERT = "INSERT INTO claim (season_id, world_uuid, cup_x, cup_y, cup_z, radius, created_by, "
            + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE = "DELETE FROM claim WHERE id = ?";
    private static final String ADD_MEMBER =
            "INSERT INTO claim_member (claim_id, uuid, added_at, added_by) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE uuid = uuid";
    private static final String REMOVE_MEMBER = "DELETE FROM claim_member WHERE claim_id = ? AND uuid = ?";
    private static final String SET_PIN = "UPDATE claim SET pin_salt = ?, pin_hash = ? WHERE id = ?";
    private static final String LOAD_CLAIMS = "SELECT id, world_uuid, cup_x, cup_y, cup_z, radius, created_by, created_at, "
            + "pin_salt, pin_hash FROM claim WHERE season_id = ?";
    private static final String LOAD_MEMBERS = "SELECT m.claim_id, m.uuid FROM claim_member m JOIN claim c ON c.id = m.claim_id "
            + "WHERE c.season_id = ?";

    /** @return l'id del nuovo claim */
    public long insert(Connection c, int season, ClaimBox box, UUID createdBy, Instant at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, season);
            ps.setBytes(2, Uuids.toBytes(box.world()));
            ps.setInt(3, box.x());
            ps.setInt(4, box.y());
            ps.setInt(5, box.z());
            ps.setInt(6, box.radius());
            ps.setBytes(7, Uuids.toBytes(createdBy));
            ps.setTimestamp(8, Timestamp.from(at));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Nessun id generato per il nuovo claim");
                }
                return keys.getLong(1);
            }
        }
    }

    /** Cancella il claim; gli autorizzati se ne vanno in cascata (D-35). @return {@code true} se la riga c'era */
    public boolean delete(Connection c, long claimId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, claimId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Idempotente: aggiungere un autorizzato già presente non cambia nulla. */
    public void addMember(Connection c, long claimId, UUID uuid, Instant at, UUID addedBy) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(ADD_MEMBER)) {
            ps.setLong(1, claimId);
            ps.setBytes(2, Uuids.toBytes(uuid));
            ps.setTimestamp(3, Timestamp.from(at));
            if (addedBy == null) {
                ps.setNull(4, Types.BINARY);
            } else {
                ps.setBytes(4, Uuids.toBytes(addedBy));
            }
            ps.executeUpdate();
        }
    }

    /** @return {@code true} se l'autorizzato c'era */
    public boolean removeMember(Connection c, long claimId, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(REMOVE_MEMBER)) {
            ps.setLong(1, claimId);
            ps.setBytes(2, Uuids.toBytes(uuid));
            return ps.executeUpdate() > 0;
        }
    }

    public void setPin(Connection c, long claimId, PinHash pin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SET_PIN)) {
            ps.setBytes(1, pin.salt());
            ps.setBytes(2, pin.hash());
            ps.setLong(3, claimId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Claim #" + claimId + " inesistente");
            }
        }
    }

    /** Tutti i claim della stagione con i loro autorizzati. */
    public List<Row> loadSeason(Connection c, int season) throws SQLException {
        Map<Long, Set<UUID>> members = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(LOAD_MEMBERS)) {
            ps.setInt(1, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.computeIfAbsent(rs.getLong(1), ignored -> new HashSet<>()).add(Uuids.fromBytes(rs.getBytes(2)));
                }
            }
        }
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(LOAD_CLAIMS)) {
            ps.setInt(1, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    ClaimBox box = new ClaimBox(Uuids.fromBytes(rs.getBytes(2)), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                            rs.getInt(6));
                    byte[] salt = rs.getBytes(9);
                    byte[] hash = rs.getBytes(10);
                    PinHash pin = salt == null || hash == null ? null : new PinHash(salt, hash);
                    rows.add(new Row(id, box, Uuids.fromBytes(rs.getBytes(7)), rs.getTimestamp(8).toInstant(), pin,
                            members.getOrDefault(id, Set.of())));
                }
            }
        }
        return rows;
    }
}
