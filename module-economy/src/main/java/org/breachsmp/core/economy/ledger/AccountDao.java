package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.OwnerType;
import org.breachsmp.core.api.util.Uuids;
import org.breachsmp.core.domain.economy.LedgerRules;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accesso alla tabella {@code account}. Il saldo cambia solo in {@link #updateBalances}, chiamato solo da
 * {@link JdbcLedgerService} dentro la transazione del ledger (I3).
 */
public final class AccountDao {

    /** Lettura senza lock di un conto. */
    public record Snapshot(long id, long balance, long version, boolean frozen) {
    }

    private record Key(int season, AccountRef ref) {
    }

    private static final String ENSURE =
            "INSERT INTO account (season_id, owner_type, owner_ref) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE id = id";
    private static final String FIND_ID = "SELECT id FROM account WHERE season_id = ? AND owner_type = ? AND owner_ref = ?";
    private static final String READ =
            "SELECT id, balance, version, frozen_at FROM account WHERE season_id = ? AND owner_type = ? AND owner_ref = ?";
    private static final String LOCK =
            "SELECT id, owner_type, owner_ref, balance, version, frozen_at FROM account WHERE id IN (%s) ORDER BY id FOR UPDATE";
    private static final String UPDATE_BALANCE = "UPDATE account SET balance = ?, version = version + 1 WHERE id = ?";
    private static final String FREEZE = "UPDATE account SET frozen_at = ? WHERE id = ? AND frozen_at IS NULL";
    private static final String UNFREEZE = "UPDATE account SET frozen_at = NULL WHERE id = ? AND frozen_at IS NOT NULL";

    private final DataSource dataSource;
    private final Map<Key, Long> ids = new ConcurrentHashMap<>();

    public AccountDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Id dei conti, creandoli se mancano, in autocommit e fuori dalla transazione del ledger (D-20). Gli id non cambiano mai. */
    public Map<AccountRef, Long> ensure(int season, Collection<AccountRef> refs) {
        Map<AccountRef, Long> result = new LinkedHashMap<>();
        List<AccountRef> missing = new ArrayList<>();
        for (AccountRef ref : refs) {
            Long id = ids.get(new Key(season, ref));
            if (id == null) {
                missing.add(ref);
            } else {
                result.put(ref, id);
            }
        }
        if (!missing.isEmpty()) {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement insert = c.prepareStatement(ENSURE);
                 PreparedStatement find = c.prepareStatement(FIND_ID)) {
                for (AccountRef ref : missing) {
                    bindOwner(insert, season, ref);
                    insert.executeUpdate();
                    bindOwner(find, season, ref);
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Conto appena creato non trovato: " + ref);
                        }
                        long id = rs.getLong(1);
                        ids.put(new Key(season, ref), id);
                        result.put(ref, id);
                    }
                }
            } catch (SQLException e) {
                throw new DataAccessException("Impossibile creare o leggere i conti " + missing + ": " + e.getMessage(), e);
            }
        }
        return result;
    }

    public long ensure(int season, AccountRef ref) {
        return ensure(season, List.of(ref)).get(ref);
    }

    /** Saldo, versione e freeze del conto; vuoto se il conto non esiste nella stagione. */
    public Optional<Snapshot> read(int season, AccountRef ref) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(READ)) {
            bindOwner(ps, season, ref);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Snapshot(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getTimestamp(4) != null));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile leggere il conto " + ref + ": " + e.getMessage(), e);
        }
    }

    /** Blocca i conti con un solo {@code SELECT … FOR UPDATE} in ordine di id (spec §4) e li restituisce in quell'ordine. */
    public List<LockedAccount> lockForUpdate(Connection c, List<Long> sortedIds) throws SQLException {
        String sql = String.format(LOCK, String.join(", ", Collections.nCopies(sortedIds.size(), "?")));
        List<LockedAccount> locked = new ArrayList<>(sortedIds.size());
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < sortedIds.size(); i++) {
                ps.setLong(i + 1, sortedIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AccountRef ref = ref(rs.getString(2), rs.getBytes(3));
                    LedgerRules.AccountState state = new LedgerRules.AccountState(rs.getLong(1), ref, rs.getLong(4),
                            rs.getTimestamp(6) != null);
                    locked.add(new LockedAccount(state, rs.getLong(5)));
                }
            }
        }
        if (locked.size() != sortedIds.size()) {
            throw new IllegalStateException("Conti mancanti durante il lock: " + sortedIds);
        }
        return locked;
    }

    /** Nuovi saldi, nello stesso ordine dei conti bloccati. Solo dentro la transazione del ledger (I3). */
    public void updateBalances(Connection c, List<LockedAccount> locked, List<Long> balances) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(UPDATE_BALANCE)) {
            for (int i = 0; i < locked.size(); i++) {
                ps.setLong(1, balances.get(i));
                ps.setLong(2, locked.get(i).state().id());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Congela o sblocca un conto. @return {@code true} se lo stato è cambiato */
    public boolean setFrozen(long accountId, boolean frozen, Instant at) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(frozen ? FREEZE : UNFREEZE)) {
            if (frozen) {
                ps.setTimestamp(1, Timestamp.from(at));
                ps.setLong(2, accountId);
            } else {
                ps.setLong(1, accountId);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile cambiare il freeze del conto " + accountId + ": " + e.getMessage(), e);
        }
    }

    static AccountRef ref(String ownerType, byte[] ownerRef) {
        return new AccountRef(OwnerType.valueOf(ownerType), Uuids.fromBytes(ownerRef));
    }

    private static void bindOwner(PreparedStatement ps, int season, AccountRef ref) throws SQLException {
        ps.setInt(1, season);
        ps.setString(2, ref.type().name());
        ps.setBytes(3, Uuids.toBytes(ref.owner()));
    }
}
