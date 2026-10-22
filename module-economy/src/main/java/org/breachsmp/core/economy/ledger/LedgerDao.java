package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.api.economy.EntryView;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.util.JsonText;
import org.breachsmp.core.api.util.Uuids;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * SQL di {@code ledger_tx} e {@code ledger_entry}, sulla connessione (e transazione) del chiamante.
 * Append-only (I3): qui ci sono solo INSERT e SELECT.
 */
public final class LedgerDao {

    private static final int DUPLICATE_KEY = 1062;

    private static final String INSERT_TX =
            "INSERT INTO ledger_tx (season_id, idem_key, kind, actor, created_at, meta) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String INSERT_ENTRY =
            "INSERT INTO ledger_entry (tx_id, seq, account_id, delta, balance_after) VALUES (?, ?, ?, ?, ?)";
    private static final String TX_BY_KEY = "SELECT id, season_id, idem_key, kind, created_at FROM ledger_tx WHERE idem_key = ?";
    private static final String TX_BY_ID = "SELECT id, season_id, idem_key, kind, created_at FROM ledger_tx WHERE id = ?";
    private static final String ENTRIES = """
            SELECT a.owner_type, a.owner_ref, e.delta, e.balance_after
            FROM ledger_entry e JOIN account a ON a.id = e.account_id
            WHERE e.tx_id = ? ORDER BY e.seq""";
    private static final String HISTORY = """
            SELECT t.id, t.created_at, t.kind, e.delta, e.balance_after
            FROM ledger_entry e JOIN ledger_tx t ON t.id = e.tx_id
            WHERE e.account_id = ? ORDER BY e.tx_id DESC LIMIT ? OFFSET ?""";

    private LedgerDao() {
    }

    /** @return l'id della nuova transazione, oppure vuoto se la chiave di idempotenza esiste già */
    public static OptionalLong insertTx(Connection c, int season, TxRequest request, Instant createdAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT_TX, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, season);
            ps.setString(2, request.idemKey().toString());
            ps.setString(3, request.kind());
            if (request.actor() == null) {
                ps.setNull(4, Types.BINARY);
            } else {
                ps.setBytes(4, Uuids.toBytes(request.actor()));
            }
            ps.setTimestamp(5, Timestamp.from(createdAt));
            if (request.meta().isEmpty()) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, JsonText.write(request.meta()));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return OptionalLong.of(keys.getLong(1));
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == DUPLICATE_KEY) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    /** Righe della transazione, con {@code seq} = 1, 2, … nell'ordine delle liste (già ordinate per id del conto). */
    public static void insertEntries(Connection c, long txId, List<LockedAccount> locked, List<Leg> legs, List<Long> balances)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT_ENTRY)) {
            for (int i = 0; i < legs.size(); i++) {
                ps.setLong(1, txId);
                ps.setInt(2, i + 1);
                ps.setLong(3, locked.get(i).state().id());
                ps.setLong(4, legs.get(i).delta());
                ps.setLong(5, balances.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static Optional<StoredTx> findByIdemKey(Connection c, UUID idemKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(TX_BY_KEY)) {
            ps.setString(1, idemKey.toString());
            return readTx(c, ps);
        }
    }

    public static Optional<StoredTx> findById(Connection c, long txId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(TX_BY_ID)) {
            ps.setLong(1, txId);
            return readTx(c, ps);
        }
    }

    /** Movimenti del conto, dal più recente. */
    public static List<HistoryRow> history(Connection c, long accountId, int limit, int offset) throws SQLException {
        List<HistoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(HISTORY)) {
            ps.setLong(1, accountId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new HistoryRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
                            rs.getLong(4), rs.getLong(5)));
                }
            }
        }
        return rows;
    }

    private static Optional<StoredTx> readTx(Connection c, PreparedStatement ps) throws SQLException {
        long id;
        int season;
        UUID key;
        String kind;
        Instant createdAt;
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            id = rs.getLong(1);
            season = rs.getInt(2);
            key = UUID.fromString(rs.getString(3));
            kind = rs.getString(4);
            createdAt = rs.getTimestamp(5).toInstant();
        }
        List<EntryView> entries = new ArrayList<>();
        try (PreparedStatement entryQuery = c.prepareStatement(ENTRIES)) {
            entryQuery.setLong(1, id);
            try (ResultSet rs = entryQuery.executeQuery()) {
                while (rs.next()) {
                    entries.add(new EntryView(AccountDao.ref(rs.getString(1), rs.getBytes(2)), rs.getLong(3), rs.getLong(4)));
                }
            }
        }
        return Optional.of(new StoredTx(id, season, key, kind, createdAt, entries));
    }
}
