package org.breachsmp.core.territory;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.CommandRegistry;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.territory.TerritoryService;
import org.breachsmp.core.api.tunable.TunableService;
import org.breachsmp.core.paper.PaperBridge;
import org.breachsmp.core.platform.audit.JdbcAuditService;
import org.breachsmp.core.platform.command.CommandHub;
import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.module.DefaultModuleContext;
import org.breachsmp.core.platform.season.CurrentSeason;
import org.breachsmp.core.platform.service.SimpleServiceRegistry;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.breachsmp.core.platform.tunable.TunableRegistry;
import org.breachsmp.core.platform.tunable.TunableView;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cablaggio del modulo con i servizi reali di piattaforma, come fa {@code ARKPlugin} (senza server). Solo {@code onLoad}:
 * {@code onEnable} registra ricette e listener, che richiedono il server (verificati nello smoke test).
 */
class TerritoryModuleIT {

    @TempDir
    Path dataFolder;

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 4, 12, 0));
    private final List<Listener> listeners = new ArrayList<>();
    private HikariDataSource dataSource;
    private PlatformExecutors executors;
    private SimpleServiceRegistry services;
    private TunableRegistry tunables;
    private CommandHub commands;
    private DefaultModuleContext context;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        executors = new PlatformExecutors(Runnable::run, 4, 1);
        Logger logger = Logger.getLogger("test");
        tunables = new TunableRegistry(dataSource, executors.db(), clock, logger);
        commands = new CommandHub();
        services = new SimpleServiceRegistry();
        services.register(SeasonService.class, CurrentSeason.load(dataSource));
        services.register(AuditService.class, new JdbcAuditService(dataSource, executors.db(), clock));
        services.register(TunableService.class, tunables);
        services.register(CommandRegistry.class, commands);
        services.register(PaperBridge.class, new PaperBridge() {
            @Override
            public Plugin plugin() {
                throw new UnsupportedOperationException("nessun server nei test");
            }

            @Override
            public void registerListener(Listener listener) {
                listeners.add(listener);
            }

            @Override
            public Path dataFolder() {
                return dataFolder;
            }
        });
        context = new DefaultModuleContext(TerritoryModule.ID, services, executors, logger, dataSource);
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
    void onLoadPublishesTerritoryServiceTunablesAndAdminCommand() throws Exception {
        TerritoryModule module = new TerritoryModule();
        module.onLoad(context);
        tunables.reload();
        assertTrue(services.find(TerritoryService.class).isPresent());
        assertEquals(List.of("territory.claim_gap", "territory.claim_radius", "territory.max_cupboards_per_player",
                "territory.pin_attempts_per_10_min"), tunables.list().stream().map(TunableView::key).toList());
        assertEquals(List.of("claim"), commands.adminSubcommands().stream().map(AdminSubcommand::name).toList());
        assertEquals(2, services.require(TerritoryService.class).tierOf("COBBLESTONE"));
        assertTrue(Files.isRegularFile(dataFolder.resolve("territory.yml")), "territory.yml di default copiato");
        assertEquals(List.of("Claim attivi: 0"), module.healthDetails());
        assertTrue(listeners.isEmpty(), "i listener si registrano solo in onEnable");
    }
}
