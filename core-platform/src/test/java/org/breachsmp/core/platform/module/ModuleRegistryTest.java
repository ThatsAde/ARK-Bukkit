package org.breachsmp.core.platform.module;

import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.api.module.ARKModule;
import org.breachsmp.core.api.module.HealthStatus;
import org.breachsmp.core.api.module.ModuleContext;
import org.breachsmp.core.api.module.ModuleDependency;
import org.breachsmp.core.domain.module.DependencyException;
import org.breachsmp.core.platform.service.SimpleServiceRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRegistryTest {

    /** I moduli finti non usano il DB: qualsiasi chiamata è un errore del test. */
    private static final DataSource NO_DATABASE = (DataSource) Proxy.newProxyInstance(ModuleRegistryTest.class.getClassLoader(),
            new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });

    private static final CoreExecutors DIRECT = new CoreExecutors() {
        @Override public Executor main() { return Runnable::run; }
        @Override public Executor db() { return Runnable::run; }
        @Override public Executor compute() { return Runnable::run; }
        @Override public Executor market() { return Runnable::run; }
    };

    private final List<String> events = new ArrayList<>();
    private final ModuleRegistry registry = new ModuleRegistry(Logger.getLogger("ModuleRegistryTest"));

    @Test
    void loadsAndEnablesInDependencyOrder() {
        load(id -> true, new FakeModule("items", events, "economy"), new FakeModule("economy", events));
        registry.enableAll();
        assertEquals(List.of("load:economy", "load:items", "enable:economy", "enable:items"), events);
        assertEquals(List.of("economy", "items"), registry.order());
    }

    @Test
    void disablesInReverseOrder() {
        load(id -> true, new FakeModule("items", events, "economy"), new FakeModule("economy", events));
        registry.enableAll();
        events.clear();
        registry.disableAll();
        assertEquals(List.of("disable:items", "disable:economy"), events);
    }

    @Test
    void disabledModuleIsSkippedAndReportedAsDisabled() {
        load(id -> !id.equals("arena"), new FakeModule("economy", events), new FakeModule("arena", events));
        registry.enableAll();
        assertEquals(List.of("load:economy", "enable:economy"), events);
        assertEquals(Map.of("economy", HealthStatus.HEALTHY, "arena", HealthStatus.DISABLED), registry.health());
    }

    @Test
    void enabledModuleDependingOnDisabledOneFailsStartup() {
        var ex = assertThrows(DependencyException.class, () -> load(id -> !id.equals("economy"),
                new FakeModule("items", events, "economy"), new FakeModule("economy", events)));
        assertTrue(ex.getMessage().contains("'items' dipende da 'economy'"));
        assertEquals(List.of(), events);
    }

    @Test
    void onLoadFailureIsWrappedWithModuleId() {
        FakeModule economy = new FakeModule("economy", events);
        economy.failOnLoad = true;
        var ex = assertThrows(ModuleStartupException.class, () -> load(id -> true, economy));
        assertTrue(ex.getMessage().startsWith("onLoad del modulo 'economy' fallito"));
    }

    @Test
    void onEnableFailureStopsStartupAndOnlyEnabledModulesAreDisabled() {
        FakeModule economy = new FakeModule("economy", events);
        FakeModule items = new FakeModule("items", events, "economy");
        items.failOnEnable = true;
        load(id -> true, economy, items);

        var ex = assertThrows(ModuleStartupException.class, registry::enableAll);
        assertTrue(ex.getMessage().startsWith("onEnable del modulo 'items' fallito"));

        events.clear();
        registry.disableAll();
        assertEquals(List.of("disable:economy"), events);
    }

    @Test
    void onDisableFailureDoesNotStopOtherModules() {
        FakeModule economy = new FakeModule("economy", events);
        FakeModule items = new FakeModule("items", events, "economy");
        items.failOnDisable = true;
        load(id -> true, economy, items);
        registry.enableAll();
        events.clear();

        registry.disableAll();
        assertEquals(List.of("disable:items", "disable:economy"), events);
    }

    @Test
    void healthExceptionIsReportedAsFailed() {
        FakeModule economy = new FakeModule("economy", events);
        economy.failOnHealth = true;
        load(id -> true, economy);
        registry.enableAll();
        assertEquals(Map.of("economy", HealthStatus.FAILED), registry.health());
    }

    @Test
    void loadedButNotEnabledIsStarting() {
        load(id -> true, new FakeModule("economy", events));
        assertEquals(Map.of("economy", HealthStatus.STARTING), registry.health());
    }

    @Test
    void afterDisableAllEveryModuleIsDisabled() {
        load(id -> true, new FakeModule("economy", events));
        registry.enableAll();
        registry.disableAll();
        assertEquals(Map.of("economy", HealthStatus.DISABLED), registry.health());
        assertEquals(List.of(), registry.order());
    }

    @Test
    void duplicateIdsAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> load(id -> true, new FakeModule("economy", events), new FakeModule("economy", events)));
    }

    @Test
    void loadCanRunOnlyOnce() {
        load(id -> true);
        assertThrows(IllegalStateException.class, () -> load(id -> true));
    }

    @Test
    void contextCarriesModuleId() {
        FakeModule economy = new FakeModule("economy", events);
        load(id -> true, economy);
        assertEquals("economy", economy.context.moduleId());
    }

    private void load(Predicate<String> enabled, FakeModule... modules) {
        registry.load(List.of(modules), enabled,
                id -> new DefaultModuleContext(id, new SimpleServiceRegistry(), DIRECT, Logger.getLogger(id), NO_DATABASE));
    }

    private static final class FakeModule implements ARKModule {

        private final String id;
        private final List<String> events;
        private final List<ModuleDependency> dependencies;
        boolean failOnLoad;
        boolean failOnEnable;
        boolean failOnDisable;
        boolean failOnHealth;
        ModuleContext context;

        FakeModule(String id, List<String> events, String... dependsOn) {
            this.id = id;
            this.events = events;
            this.dependencies = Arrays.stream(dependsOn).map(ModuleDependency::on).toList();
        }

        @Override public String id() { return id; }

        @Override public Collection<ModuleDependency> dependencies() { return dependencies; }

        @Override
        public void onLoad(ModuleContext context) {
            events.add("load:" + id);
            this.context = context;
            if (failOnLoad) throw new IllegalStateException("boom");
        }

        @Override
        public void onEnable() {
            events.add("enable:" + id);
            if (failOnEnable) throw new IllegalStateException("boom");
        }

        @Override
        public void onDisable() {
            events.add("disable:" + id);
            if (failOnDisable) throw new IllegalStateException("boom");
        }

        @Override
        public HealthStatus health() {
            if (failOnHealth) throw new IllegalStateException("boom");
            return HealthStatus.HEALTHY;
        }
    }
}
