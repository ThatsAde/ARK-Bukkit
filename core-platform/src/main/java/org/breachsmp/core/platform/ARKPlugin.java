package org.breachsmp.core.platform;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.service.ServiceRegistry;
import org.breachsmp.core.platform.command.BreachCommand;
import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.config.ConfigLoader;
import org.breachsmp.core.platform.config.CoreConfig;
import org.breachsmp.core.platform.config.DatabaseConfig;
import org.breachsmp.core.platform.db.DataSourceFactory;
import org.breachsmp.core.platform.db.Migrator;
import org.breachsmp.core.platform.health.HealthService;
import org.breachsmp.core.platform.module.DefaultModuleContext;
import org.breachsmp.core.platform.module.ModuleCatalog;
import org.breachsmp.core.platform.module.ModuleRegistry;
import org.breachsmp.core.platform.service.SimpleServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Bootstrap di BreachCore. Ogni errore di avvio disabilita il plugin (fail-closed, D-08).
 * Pool DB e migrazioni girano sincroni qui: il server è ancora in avvio e nessun giocatore è connesso (D-10).
 */
public final class ARKPlugin extends JavaPlugin {

    private static final int COMPUTE_THREADS = 2;

    private HikariDataSource dataSource;
    private PlatformExecutors executors;
    private ModuleRegistry modules;

    @Override
    public void onEnable() {
        try {
            start();
        } catch (RuntimeException e) {
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
        ServiceRegistry services = new SimpleServiceRegistry();
        modules = new ModuleRegistry(getLogger());
        modules.load(ModuleCatalog.all(), config::moduleEnabled,
                id -> new DefaultModuleContext(id, services, executors, getLogger()));
        modules.enableAll();

        HealthService health = new HealthService(modules, dataSource, executors);
        registerCommand("breach", "Amministrazione di BreachCore", new BreachCommand(health, executors.main()));
        getLogger().info("BreachCore avviato. Moduli attivi: " + modules.order());
    }
}
