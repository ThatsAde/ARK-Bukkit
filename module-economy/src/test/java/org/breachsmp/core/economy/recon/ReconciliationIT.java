package org.breachsmp.core.economy.recon;

import org.breachsmp.core.api.economy.AccountFrozenException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.domain.schedule.Schedules;
import org.breachsmp.core.economy.LedgerFixture;
import org.breachsmp.core.platform.audit.JdbcAuditService;
import org.breachsmp.core.platform.job.JdbcJobScheduler;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DoD di M1 (spec §17): la riconciliazione notturna rileva un saldo manomesso. */
class ReconciliationIT {

    private LedgerFixture f;
    private ReconciliationJob recon;

    @BeforeEach
    void setUp() {
        f = new LedgerFixture();
        JdbcAuditService audit = new JdbcAuditService(f.dataSource, f.db, f.clock);
        recon = new ReconciliationJob(f.dataSource, f.seasons, f.accounts, audit, f.clock, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        if (f != null) {
            f.close();
        }
    }

    @Test
    void cleanLedgerReportsNothing() throws Exception {
        f.give(UUID.randomUUID(), 500);
        ReconciliationJob.Report report = recon.runNow();
        assertTrue(report.clean());
        assertEquals(2, report.accounts());
        assertFalse(recon.systemProblem());
        assertEquals(0, f.count("SELECT COUNT(*) FROM audit_log"));
        assertTrue(recon.healthLines().getFirst().startsWith("Riconciliazione: OK"));
    }

    @Test
    void tamperedPlayerBalanceIsDetectedAndFrozen() throws Exception {
        UUID alice = UUID.randomUUID();
        f.give(alice, 500);
        f.execute("UPDATE account SET balance = balance + 100 WHERE owner_type = 'PLAYER'"); // manomissione fuori dal ledger
        ReconciliationJob.Report report = recon.runNow();
        assertEquals(1, report.mismatches().size());
        assertEquals(new ReconciliationJob.Mismatch(report.mismatches().getFirst().accountId(), AccountRef.player(alice), 600, 500),
                report.mismatches().getFirst());
        assertTrue(f.accounts.read(1, AccountRef.player(alice)).orElseThrow().frozen());
        assertEquals(1, f.count("SELECT COUNT(*) FROM audit_log WHERE action = 'RECON_MISMATCH'"));
        ExecutionException error = assertThrows(ExecutionException.class, () -> f.ledger.execute(f.tx(TxKinds.PAY,
                new Leg(AccountRef.player(alice), -1), new Leg(AccountRef.player(UUID.randomUUID()), 1))).get(5, SECONDS));
        assertInstanceOf(AccountFrozenException.class, error.getCause());
        assertTrue(recon.systemProblem(), "la somma dei saldi non è più 0");
    }

    @Test
    void tamperedSystemAccountIsNotFrozenButDegradesHealth() throws Exception {
        f.give(UUID.randomUUID(), 500);
        f.execute("UPDATE account SET balance = balance - 7 WHERE owner_type = 'SYSTEM'");
        ReconciliationJob.Report report = recon.runNow();
        assertEquals(AccountRef.system(SystemAccount.FAUCET), report.mismatches().getFirst().account());
        assertFalse(f.accounts.read(1, AccountRef.system(SystemAccount.FAUCET)).orElseThrow().frozen());
        assertTrue(recon.systemProblem());
        assertTrue(recon.healthLines().getFirst().contains("discrepanze"));
    }

    @Test
    void nightlyJobRunsThroughTheSchedulerAt4() throws Exception {
        MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 22, 12, 0));
        JdbcJobScheduler scheduler = new JdbcJobScheduler(f.dataSource, f.db, clock, "p1", Logger.getLogger("test"));
        scheduler.register(ReconciliationJob.ID, now -> Schedules.nextDaily(now, 4), recon);
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            scheduler.start(timer);
            assertEquals(List.of(), scheduler.tick());
            clock.set(LocalDateTime.of(2026, 10, 23, 4, 0));
            assertEquals(List.of(ReconciliationJob.ID), scheduler.tick());
            assertTrue(recon.lastReport().isPresent());
        } finally {
            timer.shutdownNow();
        }
    }
}
