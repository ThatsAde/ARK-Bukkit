package org.breachsmp.core.platform.tunable;

import org.breachsmp.core.api.audit.AuditEntry;
import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.tunable.Tunable;
import org.breachsmp.core.api.tunable.TunableService;
import org.breachsmp.core.api.util.Uuids;
import org.breachsmp.core.platform.audit.JdbcAuditService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Tunable interi nel DB (spec §3, D-17): valore in memoria, ricaricato dal DB; ogni modifica scrive l'audit nella stessa transazione. */
public final class TunableRegistry implements TunableService {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.]{0,63}");
    private static final String SELECT_ALL = "SELECT tkey, value FROM tunable";
    private static final String UPSERT = """
            INSERT INTO tunable (tkey, value, updated_at, updated_by) VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at), updated_by = VALUES(updated_by)""";

    private final DataSource dataSource;
    private final Executor db;
    private final Clock clock;
    private final Logger logger;
    private final Map<String, LongTunable> tunables = new ConcurrentSkipListMap<>();

    public TunableRegistry(DataSource dataSource, Executor db, Clock clock, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.db = Objects.requireNonNull(db, "db");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Tunable registerLong(String key, long defaultValue, long min, long max, String description) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Chiave tunable non valida: '" + key + "' (formato [a-z][a-z0-9_.]{0,63})");
        }
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Tunable " + key + ": default " + defaultValue + " fuori da [" + min + ", " + max + "]");
        }
        LongTunable tunable = new LongTunable(key, defaultValue, min, max, Objects.requireNonNull(description, "description"));
        if (tunables.putIfAbsent(key, tunable) != null) {
            throw new IllegalStateException("Tunable già registrato: " + key);
        }
        return tunable;
    }

    /** Rilegge tutti i valori (bloccante). Riga assente → default; valore non valido → default e WARNING. */
    public void reload() {
        Map<String, String> stored = new HashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                stored.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile leggere i tunable: " + e.getMessage(), e);
        }
        for (LongTunable tunable : tunables.values()) {
            String raw = stored.get(tunable.key);
            if (raw == null) {
                tunable.value = tunable.defaultValue;
                continue;
            }
            try {
                tunable.value = tunable.parse(raw);
            } catch (IllegalArgumentException e) {
                tunable.value = tunable.defaultValue;
                logger.warning("Tunable " + tunable.key + ": valore nel DB non valido (" + e.getMessage()
                        + "), uso il default " + tunable.defaultValue);
            }
        }
    }

    /** Come {@link #reload()}, ma un errore viene solo registrato (ricarica periodica dal timer, D-26). */
    public void reloadQuietly() {
        try {
            reload();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Ricarica dei tunable fallita, restano i valori precedenti", e);
        }
    }

    /** Valida e salva sul thread DB; errori di input → {@link IllegalArgumentException} (nel future). */
    public CompletableFuture<TunableChange> set(String key, String raw, UUID actor) {
        return CompletableFuture.supplyAsync(() -> setNow(key, raw, actor), db);
    }

    private TunableChange setNow(String key, String raw, UUID actor) {
        LongTunable tunable = tunables.get(key);
        if (tunable == null) {
            throw new IllegalArgumentException("Tunable sconosciuto: " + key);
        }
        long value = tunable.parse(raw);
        long previous = tunable.value;
        Instant now = clock.instant();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(UPSERT)) {
                    ps.setString(1, key);
                    ps.setString(2, Long.toString(value));
                    ps.setTimestamp(3, Timestamp.from(now));
                    if (actor == null) {
                        ps.setNull(4, Types.BINARY);
                    } else {
                        ps.setBytes(4, Uuids.toBytes(actor));
                    }
                    ps.executeUpdate();
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("old", previous);
                detail.put("new", value);
                JdbcAuditService.insert(c, new AuditEntry(actor, "TUNABLE_SET", key, detail), now);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile salvare il tunable " + key + ": " + e.getMessage(), e);
        }
        tunable.value = value;
        return new TunableChange(tunable.view(), previous);
    }

    public List<TunableView> list() {
        return tunables.values().stream().map(LongTunable::view).toList();
    }

    public Optional<TunableView> find(String key) {
        return Optional.ofNullable(tunables.get(key)).map(LongTunable::view);
    }

    private static final class LongTunable implements Tunable {

        private final String key;
        private final long defaultValue;
        private final long min;
        private final long max;
        private final String description;
        private volatile long value;

        private LongTunable(String key, long defaultValue, long min, long max, String description) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.description = description;
            this.value = defaultValue;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public long get() {
            return value;
        }

        long parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("valore vuoto");
            }
            long parsed;
            try {
                parsed = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + raw.trim() + "' non è un numero intero");
            }
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(parsed + " è fuori dall'intervallo [" + min + ", " + max + "]");
            }
            return parsed;
        }

        TunableView view() {
            return new TunableView(key, value, defaultValue, min, max, description);
        }
    }
}
