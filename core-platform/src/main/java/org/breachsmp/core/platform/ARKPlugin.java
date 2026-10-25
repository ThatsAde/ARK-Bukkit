package org.breachsmp.core.platform;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.CommandRegistry;
import org.breachsmp.core.api.command.ModuleCommand;
import org.breachsmp.core.api.job.JobScheduler;
import org.breachsmp.core.api.lock.PlayerLock;
import org.breachsmp.core.api.player.PlayerHooks;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.service.ServiceRegistry;
import org.breachsmp.core.api.tunable.TunableService;
import org.breachsmp.core.paper.PaperBridge;
import org.breachsmp.core.platform.audit.JdbcAuditService;
import org.breachsmp.core.platform.command.BreachCommand;
import org.breachsmp.core.platform.command.CommandDelivery;
import org.breachsmp.core.platform.command.CommandHub;
import org.breachsmp.core.platform.command.HealthSubcommand;
import org.breachsmp.core.platform.command.ModuleCommandAdapter;
import org.breachsmp.core.platform.command.TunableSubcommand;
import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.config.ConfigLoader;
import org.breachsmp.core.platform.config.CoreConfig;
import org.breachsmp.core.platform.config.DatabaseConfig;
import org.breachsmp.core.platform.db.DataSourceFactory;
import org.breachsmp.core.platform.db.Migrator;
import org.breachsmp.core.platform.health.HealthService;
import org.breachsmp.core.platform.job.JdbcJobScheduler;
import org.breachsmp.core.platform.lock.SerialPlayerLock;
import org.breachsmp.core.platform.module.DefaultModuleContext;
import org.breachsmp.core.platform.module.ModuleCatalog;
import org.breachsmp.core.platform.module.ModuleRegistry;
import org.breachsmp.core.platform.paper.BukkitPaperBridge;
import org.breachsmp.core.platform.player.JdbcPlayerService;
import org.breachsmp.core.platform.player.LoginGate;
import org.breachsmp.core.platform.player.PlayerHookRegistry;
import org.breachsmp.core.platform.player.PlayerListener;
import org.breachsmp.core.platform.season.CurrentSeason;
import org.breachsmp.core.platform.service.SimpleServiceRegistry;
import org.breachsmp.core.platform.tunable.TunableRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bootstrap di BreachCore. Ogni errore di avvio disabilita il plugin (fail-closed, D-08).
 * Pool DB, migrazioni e letture iniziali girano sincroni qui: il server è ancora in avvio e nessun giocatore è connesso (D-10).
 *
 * <p>Sequenza: config → pool → migrazioni → executor → servizi di piattaforma nel registry → {@code onLoad} dei moduli →
 * ricarica dei tunable → {@code enableAll} → listener di login → comandi → scheduler dei job e ricarica periodica dei tunable.
 */
public final class ARKPlugin extends JavaPlugin {

    private static final int COMPUTE_THREADS = 2;
    private static final long TUNABLE_RELOAD_SECONDS = 30;

    private HikariDataSource dataSource;
    private PlatformExecutors executors;
    private ModuleRegistry modules;

    @Override
    public void onEnable() {
        try {
            start();
        } catch (RuntimeException | LinkageError | ServiceConfigurationError e) {
            getLogger().log(Level.SEVERE, "Avvio di BreachCore fallito, il plugin viene disabilitato: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Ordine inverso dell'avvio: moduli → executor (drain) → pool DB per ultimo
        if (modules != null) {
            modules.disableAll();
            modules = null;
        }
        if (executors != null) {
            executors.close();
            executors = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void start() {
        saveDefaultConfig();
        Path dataDir = getDataFolder().toPath();
        CoreConfig config = ConfigLoader.load(dataDir.resolve("config.yml"), dataDir);
        DatabaseConfig db = config.database();
        int cores = Runtime.getRuntime().availableProcessors();

        dataSource = DataSourceFactory.create(db, ConfigLoader.readPassword(db.passwordFile()), cores);
        int applied = new Migrator(dataSource, getClassLoader()).migrate();
        getLogger().info("Database pronto, migrazioni applicate ora: " + applied);

        executors = new PlatformExecutors(task -> getServer().getScheduler().runTask(this, task),
                db.effectivePoolSize(cores), COMPUTE_THREADS);
        Clock clock = Clock.systemDefaultZone();
        Logger logger = getLogger();

        // Servizi di piattaforma (D-15…D-18, D-26): registrati prima dei moduli, che li richiedono in onLoad
        CurrentSeason season = CurrentSeason.load(dataSource);
        JdbcPlayerService players = new JdbcPlayerService(dataSource, executors.db(), clock);
        JdbcAuditService audit = new JdbcAuditService(dataSource, executors.db(), clock);
        TunableRegistry tunables = new TunableRegistry(dataSource, executors.db(), clock, logger);
        JdbcJobScheduler jobs = new JdbcJobScheduler(dataSource, executors.db(), clock, JdbcJobScheduler.newProcessId(), logger);
        PlayerHookRegistry hooks = new PlayerHookRegistry(logger);
        CommandHub commands = new CommandHub();

        ServiceRegistry services = new SimpleServiceRegistry();
        services.register(SeasonService.class, season);
        services.register(PlayerService.class, players);
        services.register(AuditService.class, audit);
        services.register(TunableService.class, tunables);
        services.register(JobScheduler.class, jobs);
        services.register(PlayerHooks.class, hooks);
        services.register(CommandRegistry.class, commands);
        services.register(PlayerLock.class, new SerialPlayerLock());
        services.register(PaperBridge.class, new BukkitPaperBridge(this));

        modules = new ModuleRegistry(logger);
        modules.load(ModuleCatalog.all(), config::moduleEnabled,
                id -> new DefaultModuleContext(id, services, executors, logger, dataSource));
        tunables.reload(); // i moduli hanno registrato i loro tunable in onLoad
        modules.enableAll();
        commands.close();

        getServer().getPluginManager().registerEvents(
                new PlayerListener(new LoginGate(players::upsert, hooks, logger), hooks), this);

        CommandDelivery delivery = new CommandDelivery(executors.main(), getServer(), logger);
        HealthService health = new HealthService(modules, dataSource, executors, jobs::healthLines);
        List<AdminSubcommand> admin = new ArrayList<>(List.of(new HealthSubcommand(health), new TunableSubcommand(tunables)));
        admin.addAll(commands.adminSubcommands());
        registerCommand("breach", "Amministrazione di BreachCore", new BreachCommand(admin, delivery, getServer()));
        for (ModuleCommand command : commands.commands()) {
            registerCommand(command.label(), command.description(), command.aliases(),
                    new ModuleCommandAdapter(command, delivery, getServer()));
        }

        jobs.start(executors.timer());
        executors.timer().scheduleWithFixedDelay(() -> executors.db().execute(tunables::reloadQuietly),
                TUNABLE_RELOAD_SECONDS, TUNABLE_RELOAD_SECONDS, TimeUnit.SECONDS);
        logger.info("BreachCore avviato. Moduli attivi: " + modules.order());
    }
}
