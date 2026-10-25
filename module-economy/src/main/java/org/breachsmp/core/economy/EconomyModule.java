package org.breachsmp.core.economy;

import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.command.CommandRegistry;
import org.breachsmp.core.api.economy.LedgerService;
import org.breachsmp.core.api.job.JobScheduler;
import org.breachsmp.core.api.lock.PlayerLock;
import org.breachsmp.core.api.module.ARKModule;
import org.breachsmp.core.api.module.HealthStatus;
import org.breachsmp.core.api.module.ModuleContext;
import org.breachsmp.core.api.player.PlayerHooks;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.service.ServiceRegistry;
import org.breachsmp.core.api.tunable.TunableService;
import org.breachsmp.core.domain.economy.Money;
import org.breachsmp.core.domain.ratelimit.SlidingWindowLimiter;
import org.breachsmp.core.domain.schedule.Schedules;
import org.breachsmp.core.economy.admin.EconomyAdmin;
import org.breachsmp.core.economy.command.BalanceCommand;
import org.breachsmp.core.economy.command.EcoSubcommand;
import org.breachsmp.core.economy.command.FreezeSubcommand;
import org.breachsmp.core.economy.command.PayCommand;
import org.breachsmp.core.economy.command.ReconSubcommand;
import org.breachsmp.core.economy.command.RollbackSubcommand;
import org.breachsmp.core.economy.command.TxSubcommand;
import org.breachsmp.core.economy.ledger.AccountDao;
import org.breachsmp.core.economy.ledger.BalanceCache;
import org.breachsmp.core.economy.ledger.JdbcLedgerService;
import org.breachsmp.core.economy.recon.ReconciliationJob;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Modulo economia (M1): ledger, conti, {@code /balance}, {@code /pay}, strumenti dello staff e riconciliazione notturna.
 * Tutto si registra in {@code onLoad}; la piattaforma attiva comandi, hook e job dopo {@code enableAll()}.
 */
public final class EconomyModule implements ARKModule {

    public static final String ID = "economy";

    private volatile JdbcLedgerService ledger;
    private volatile EconomyAdmin admin;
    private volatile ReconciliationJob recon;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onLoad(ModuleContext context) {
        ServiceRegistry services = context.services();
        SeasonService seasons = services.require(SeasonService.class);
        PlayerService players = services.require(PlayerService.class);
        AuditService audit = services.require(AuditService.class);
        JobScheduler jobs = services.require(JobScheduler.class);
        PlayerHooks hooks = services.require(PlayerHooks.class);
        CommandRegistry commands = services.require(CommandRegistry.class);
        PlayerLock lock = services.require(PlayerLock.class);
        EconomyTunables tunables = EconomyTunables.register(services.require(TunableService.class));

        Clock clock = Clock.systemDefaultZone();
        DataSource dataSource = context.dataSource();
        Executor db = context.executors().db();
        AccountDao accounts = new AccountDao(dataSource);
        BalanceCache cache = new BalanceCache();
        ledger = new JdbcLedgerService(dataSource, db, seasons, accounts, cache, clock, context.logger());
        services.register(LedgerService.class, ledger);

        admin = new EconomyAdmin(dataSource, db, seasons, accounts, ledger, audit, clock, context.logger());
        recon = new ReconciliationJob(dataSource, seasons, accounts, audit, clock, context.logger());
        SlidingWindowLimiter payLimiter = new SlidingWindowLimiter(clock, Duration.ofMinutes(1));
        EconomyLogin login = new EconomyLogin(seasons, accounts, ledger, tunables, cache, payLimiter);
        hooks.onLogin(ID, login);
        hooks.onQuit(ID, login);

        commands.register(new BalanceCommand(ledger, players));
        commands.register(new PayCommand(ledger, players, lock, tunables, payLimiter));
        commands.registerAdmin(new EcoSubcommand(admin, players));
        commands.registerAdmin(new TxSubcommand(admin, players));
        commands.registerAdmin(new RollbackSubcommand(admin));
        commands.registerAdmin(new FreezeSubcommand(true, admin, players));
        commands.registerAdmin(new FreezeSubcommand(false, admin, players));
        commands.registerAdmin(new ReconSubcommand(recon, db));

        jobs.register(ReconciliationJob.ID, now -> Schedules.nextDaily(now, (int) tunables.reconHour().get()), recon);
    }

    @Override
    public void onEnable() {
        // Niente da avviare: comandi, hook e job li attiva la piattaforma dopo enableAll()
    }

    @Override
    public void onDisable() {
        // Il DB è la fonte di verità (I1): niente da svuotare; nessun lavoro sul main durante lo spegnimento
    }

    @Override
    public HealthStatus health() {
        ReconciliationJob job = recon;
        return job != null && job.systemProblem() ? HealthStatus.DEGRADED : HealthStatus.HEALTHY;
    }

    @Override
    public List<String> healthDetails() {
        List<String> lines = new ArrayList<>();
        lines.add("Massa monetaria: " + Money.format(admin.moneySupplyNow()));
        lines.addAll(recon.healthLines());
        lines.add("Retry deadlock del ledger: " + ledger.deadlockRetries());
        return lines;
    }
}
