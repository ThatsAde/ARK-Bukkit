package org.breachsmp.core.platform.job;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.job.Job;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcJobSchedulerIT {

    private HikariDataSource dataSource;
    private ExecutorService pool;
    private ScheduledExecutorService timer;
    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 22, 3, 0));

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        pool = Executors.newFixedThreadPool(4);
        timer = Executors.newSingleThreadScheduledExecutor(); // i tick si chiamano a mano: il timer non arriva mai a 60 s
    }

    @AfterEach
    void tearDown() {
        if (timer != null) {
            timer.shutdownNow();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void runsADueJobOnceAndSchedulesTheNextRun() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        JdbcJobScheduler scheduler = scheduler("p1");
        scheduler.register("test.hourly", now -> now.plusHours(1), runs::incrementAndGet);
        scheduler.start(timer);                                      // next_run_at = 04:00
        assertEquals(List.of(), scheduler.tick());                   // alle 03:00 non è dovuto
        clock.set(LocalDateTime.of(2026, 10, 22, 4, 0));
        assertEquals(List.of("test.hourly"), scheduler.tick());
        assertEquals(List.of(), scheduler.tick());                   // già eseguito, prossimo alle 05:00
        assertEquals(1, runs.get());
        assertEquals("2026-10-22T05:00", queryString("SELECT next_run_at FROM scheduled_job").replace(' ', 'T').substring(0, 16));
        assertEquals("OK", queryString("SELECT last_status FROM scheduled_job"));
        assertNull(queryString("SELECT locked_by FROM scheduled_job"));
        assertTrue(scheduler.healthLines().getFirst().startsWith("Ultimo job: test.hourly 2026-10-22 04:00:00 OK"));
    }

    @Test
    void twoSchedulersRunADueJobOnce() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        Job slow = () -> {
            runs.incrementAndGet();
            Thread.sleep(300);
        };
        JdbcJobScheduler first = scheduler("p1");
        JdbcJobScheduler second = scheduler("p2");
        first.register("test.once", now -> now.plusDays(1), slow);
        second.register("test.once", now -> now.plusDays(1), slow);
        first.start(timer);
        second.start(timer);
        clock.set(LocalDateTime.of(2026, 10, 23, 3, 0));
        CompletableFuture<List<String>> a = CompletableFuture.supplyAsync(first::tick, pool);
        CompletableFuture<List<String>> b = CompletableFuture.supplyAsync(second::tick, pool);
        assertEquals(1, a.get(10, SECONDS).size() + b.get(10, SECONDS).size());
        assertEquals(1, runs.get());
    }

    @Test
    void failedJobIsRecordedAndRescheduled() throws Exception {
        JdbcJobScheduler scheduler = scheduler("p1");
        scheduler.register("test.fail", now -> now.plusHours(1), () -> {
            throw new IllegalStateException("esploso");
        });
        scheduler.start(timer);
        clock.set(LocalDateTime.of(2026, 10, 22, 4, 0));
        assertEquals(List.of("test.fail"), scheduler.tick());
        assertEquals("FAILED", queryString("SELECT last_status FROM scheduled_job"));
        assertTrue(queryString("SELECT last_error FROM scheduled_job").contains("esploso"));
        assertTrue(scheduler.healthLines().getFirst().contains("FAILED (esploso)"));
    }

    @Test
    void expiredLeaseOfAnotherProcessIsTakenOver() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        JdbcJobScheduler scheduler = scheduler("p1");
        scheduler.register("test.lease", now -> now.plusHours(1), runs::incrementAndGet);
        scheduler.start(timer);
        execute("UPDATE scheduled_job SET locked_by = 'morto', locked_until = '2026-10-22 03:30:00'");
        clock.set(LocalDateTime.of(2026, 10, 22, 4, 0));
        assertEquals(List.of("test.lease"), scheduler.tick());
        assertEquals(1, runs.get());
    }

    @Test
    void registerAfterStartIsRefused() {
        JdbcJobScheduler scheduler = scheduler("p1");
        scheduler.start(timer);
        assertThrows(IllegalStateException.class, () -> scheduler.register("late", now -> now.plusHours(1), () -> {
        }));
    }

    @Test
    void healthLineWhenNothingRanYet() {
        assertEquals(List.of("Ultimo job: nessuno"), scheduler("p1").healthLines());
    }

    private JdbcJobScheduler scheduler(String processId) {
        return new JdbcJobScheduler(dataSource, pool, clock, processId, Logger.getLogger("test"));
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
