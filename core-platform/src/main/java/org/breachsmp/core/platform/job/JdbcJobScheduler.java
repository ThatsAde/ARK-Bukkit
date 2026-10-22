package org.breachsmp.core.platform.job;

import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.job.Job;
import org.breachsmp.core.api.job.JobScheduler;
import org.breachsmp.core.api.job.NextRun;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Job pianificati su {@code scheduled_job} con lease (spec §9, E19): un job gira solo se è dovuto e se questo processo
 * prende il lease con un {@code UPDATE} condizionato, quindi con più processi gira una volta sola.
 * Il timer di piattaforma controlla ogni 60 s; il lavoro gira sul thread DB (D-26).
 */
public final class JdbcJobScheduler implements JobScheduler {

    static final Duration LEASE = Duration.ofMinutes(10);
    static final long TICK_SECONDS = 60;

    private static final int MAX_ERROR_LENGTH = 255;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INSERT_JOB =
            "INSERT INTO scheduled_job (job_id, next_run_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE job_id = job_id";
    private static final String ACQUIRE = """
            UPDATE scheduled_job SET locked_by = ?, locked_until = ?
            WHERE job_id = ? AND next_run_at <= ? AND (locked_until IS NULL OR locked_until < ?)""";
    private static final String RELEASE = """
            UPDATE scheduled_job
            SET locked_by = NULL, locked_until = NULL, last_run_at = ?, last_status = ?, last_error = ?, next_run_at = ?
            WHERE job_id = ? AND locked_by = ?""";
    private static final String LAST_RUN = """
            SELECT job_id, last_run_at, last_status, last_error FROM scheduled_job
            WHERE last_run_at IS NOT NULL ORDER BY last_run_at DESC LIMIT 1""";

    private record Registration(NextRun nextRun, Job job) {
    }

    private final DataSource dataSource;
    private final Executor db;
    private final Clock clock;
    private final String processId;
    private final Logger logger;
    private final Map<String, Registration> jobs = new ConcurrentSkipListMap<>();
    private volatile boolean started;

    public JdbcJobScheduler(DataSource dataSource, Executor db, Clock clock, String processId, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.db = Objects.requireNonNull(db, "db");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.processId = Objects.requireNonNull(processId, "processId");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Identificativo di questo processo per {@code locked_by}. */
    public static String newProcessId() {
        return "breach-" + UUID.randomUUID();
    }

    @Override
    public void register(String jobId, NextRun nextRun, Job job) {
        if (started) {
            throw new IllegalStateException("Lo scheduler è già partito: registra i job in onLoad o onEnable");
        }
        if (jobId == null || jobId.isBlank() || jobId.length() > 64) {
            throw new IllegalArgumentException("Id job non valido: '" + jobId + "' (1-64 caratteri)");
        }
        Registration registration = new Registration(Objects.requireNonNull(nextRun, "nextRun"), Objects.requireNonNull(job, "job"));
        if (jobs.putIfAbsent(jobId, registration) != null) {
            throw new IllegalStateException("Job già registrato: " + jobId);
        }
    }

    /** Inserisce i job mancanti (bloccante, all'avvio) e programma il controllo ogni 60 s; il lavoro gira sul thread DB. */
    public void start(ScheduledExecutorService timer) {
        started = true;
        if (!jobs.isEmpty()) {
            LocalDateTime now = LocalDateTime.now(clock);
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(INSERT_JOB)) {
                for (Map.Entry<String, Registration> entry : jobs.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setObject(2, entry.getValue().nextRun().after(now));
                    ps.addBatch();
                }
                ps.executeBatch(); // un job già presente mantiene il suo next_run_at
            } catch (SQLException e) {
                throw new DataAccessException("Impossibile registrare i job pianificati: " + e.getMessage(), e);
            }
        }
        timer.scheduleWithFixedDelay(() -> db.execute(this::tickQuietly), TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
    }

    /** Esegue i job dovuti di cui ottiene il lease. Bloccante; usato dal timer e dai test. @return id dei job eseguiti */
    public List<String> tick() {
        List<String> ran = new ArrayList<>();
        for (Map.Entry<String, Registration> entry : jobs.entrySet()) {
            if (runIfDue(entry.getKey(), entry.getValue())) {
                ran.add(entry.getKey());
            }
        }
        return ran;
    }

    /** Una riga per {@code /breach health}: l'ultimo job eseguito da qualsiasi processo. Bloccante (thread DB). */
    public List<String> healthLines() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(LAST_RUN);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return List.of("Ultimo job: nessuno");
            }
            String line = "Ultimo job: " + rs.getString(1) + " " + FORMAT.format(rs.getObject(2, LocalDateTime.class))
                    + " " + rs.getString(3);
            String error = rs.getString(4);
            return List.of(error == null ? line : line + " (" + error + ")");
        } catch (SQLException e) {
            return List.of("Ultimo job: non disponibile (" + e.getMessage() + ")");
        }
    }

    private void tickQuietly() {
        try {
            tick();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Controllo dei job fallito", e);
        }
    }

    private boolean runIfDue(String jobId, Registration registration) {
        if (!acquire(jobId, LocalDateTime.now(clock))) {
            return false;
        }
        String status = "OK";
        String error = null;
        try {
            registration.job().run();
        } catch (Exception e) {
            status = "FAILED";
            error = truncate(describe(e));
            logger.log(Level.SEVERE, "Job " + jobId + " fallito", e);
        }
        LocalDateTime finished = LocalDateTime.now(clock);
        release(jobId, finished, status, error, registration.nextRun().after(finished));
        return true;
    }

    private boolean acquire(String jobId, LocalDateTime now) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(ACQUIRE)) {
            ps.setString(1, processId);
            ps.setObject(2, now.plus(LEASE));
            ps.setString(3, jobId);
            ps.setObject(4, now);
            ps.setObject(5, now);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile prendere il lease del job " + jobId + ": " + e.getMessage(), e);
        }
    }

    private void release(String jobId, LocalDateTime finished, String status, String error, LocalDateTime next) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(RELEASE)) {
            ps.setObject(1, finished);
            ps.setString(2, status);
            ps.setString(3, error);
            ps.setObject(4, next);
            ps.setString(5, jobId);
            ps.setString(6, processId);
            if (ps.executeUpdate() != 1) {
                logger.warning("Job " + jobId + ": il lease era scaduto ed è stato preso da un altro processo");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile chiudere il lease del job " + jobId + ": " + e.getMessage(), e);
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
