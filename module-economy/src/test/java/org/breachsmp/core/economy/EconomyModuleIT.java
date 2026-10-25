package org.breachsmp.core.economy;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.CommandRegistry;
import org.breachsmp.core.api.command.ModuleCommand;
import org.breachsmp.core.api.economy.LedgerService;
import org.breachsmp.core.api.job.JobScheduler;
import org.breachsmp.core.api.lock.PlayerLock;
import org.breachsmp.core.api.module.HealthStatus;
import org.breachsmp.core.api.player.PlayerHooks;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.tunable.TunableService;
import org.breachsmp.core.economy.recon.ReconciliationJob;
import org.breachsmp.core.platform.audit.JdbcAuditService;
import org.breachsmp.core.platform.command.CommandHub;
import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.job.JdbcJobScheduler;
import org.breachsmp.core.platform.lock.SerialPlayerLock;
import org.breachsmp.core.platform.module.DefaultModuleContext;
import org.breachsmp.core.platform.player.JdbcPlayerService;
import org.breachsmp.core.platform.player.PlayerHookRegistry;
import org.breachsmp.core.platform.season.CurrentSeason;
import org.breachsmp.core.platform.service.SimpleServiceRegistry;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.breachsmp.core.platform.tunable.TunableRegistry;
import org.breachsmp.core.platform.tunable.TunableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cablaggio del modulo con i servizi reali di piattaforma, come fa {@code ARKPlugin} (senza server). */
class EconomyModuleIT {

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 22, 12, 0));
    private HikariDataSource dataSource;
    private PlatformExecutors executors;
    private SimpleServiceRegistry services;
    private TunableRegistry tunables;
    private JdbcJobScheduler jobs;
    private PlayerHookRegistry hooks;
    private CommandHub commands;
    private DefaultModuleContext context;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        executors = new PlatformExecutors(Runnable::run, 4, 1);
        Logger logger = Logger.getLogger("test");
        tunables = new TunableRegistry(dataSource, executors.db(), clock, logger);
        jobs = new JdbcJobScheduler(dataSource, executors.db(), clock, "p1", logger);
        hooks = new PlayerHookRegistry(logger);
        commands = new CommandHub();
        services = new SimpleServiceRegistry();
        services.register(SeasonService.class, CurrentSeason.load(dataSource));
        services.register(PlayerService.class, new JdbcPlayerService(dataSource, executors.db(), clock));
        services.register(AuditService.class, new JdbcAuditService(dataSource, executors.db(), clock));
        services.register(TunableService.class, tunables);
        services.register(JobScheduler.class, jobs);
        services.register(PlayerHooks.class, hooks);
        services.register(CommandRegistry.class, commands);
        services.register(PlayerLock.class, new SerialPlayerLock());
        context = new DefaultModuleContext(EconomyModule.ID, services, executors, logger, dataSource);
    }

    @AfterEach
    void tearDown() {
        if (executors != null) {
            executors.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void onLoadRegistersLedgerCommandsTunablesHooksAndTheNightlyJob() throws Exception {
        EconomyModule module = new EconomyModule();
        module.onLoad(context);
        tunables.reload();
        module.onEnable();
        commands.close();

        assertTrue(services.find(LedgerService.class).isPresent());
        assertEquals(List.of("balance", "pay"), commands.commands().stream().map(ModuleCommand::label).sorted().toList());
        assertEquals(List.of("eco", "freeze", "recon", "rollback", "tx", "unfreeze"),
                commands.adminSubcommands().stream().map(AdminSubcommand::name).sorted().toList());
        assertEquals(List.of("economy.pay_rate_per_minute", "economy.recon_hour", "economy.starting_balance", "economy.transfer_fee_bp"),
                tunables.list().stream().map(TunableView::key).toList());

        UUID alice = UUID.randomUUID();
        hooks.runLogin(alice, "Alice");
        assertEquals(1, count("SELECT COUNT(*) FROM account WHERE owner_type = 'PLAYER'"));

        jobs.start(executors.timer());
        clock.set(LocalDateTime.of(2026, 10, 23, 4, 0));
        assertEquals(List.of(ReconciliationJob.ID), jobs.tick());
        assertEquals(HealthStatus.HEALTHY, module.health());
        List<String> details = module.healthDetails();
        assertEquals("Massa monetaria: 0 monete", details.getFirst());
        assertTrue(details.get(1).startsWith("Riconciliazione: OK"), details.get(1));
        assertEquals("Retry deadlock del ledger: 0", details.get(2));
    }

    private long count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
