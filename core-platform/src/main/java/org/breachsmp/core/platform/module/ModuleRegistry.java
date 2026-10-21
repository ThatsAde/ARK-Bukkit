package org.breachsmp.core.platform.module;

import org.breachsmp.core.api.module.ARKModule;
import org.breachsmp.core.api.module.HealthStatus;
import org.breachsmp.core.api.module.ModuleContext;
import org.breachsmp.core.api.module.ModuleDependency;
import org.breachsmp.core.domain.module.DependencyResolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ciclo di vita dei moduli: ordine per dipendenze, feature flag, avvio fail-closed, arresto in ordine inverso.
 * {@code load}/{@code enableAll}/{@code disableAll} si chiamano dal main thread; {@code health()} da qualsiasi thread.
 */
public final class ModuleRegistry {

    private final Logger logger;
    private final Map<String, ARKModule> catalog = new LinkedHashMap<>();
    private final List<ARKModule> loaded = new CopyOnWriteArrayList<>();
    private final List<ARKModule> enabled = new CopyOnWriteArrayList<>();
    private boolean loadCalled;

    public ModuleRegistry(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Ordina i moduli abilitati per dipendenze e chiama {@code onLoad} su ciascuno.
     *
     * @throws org.breachsmp.core.domain.module.DependencyException dipendenza mancante/disabilitata o ciclo
     * @throws ModuleStartupException se un {@code onLoad} lancia un'eccezione
     */
    public void load(Collection<? extends ARKModule> modules, Predicate<String> isEnabled,
                     Function<String, ModuleContext> contextFactory) {
        if (loadCalled) {
            throw new IllegalStateException("load() è già stato eseguito");
        }
        loadCalled = true;
        for (ARKModule module : modules) {
            if (catalog.putIfAbsent(module.id(), module) != null) {
                throw new IllegalStateException("Id modulo duplicato: " + module.id());
            }
        }

        Map<String, List<String>> graph = new HashMap<>();
        for (ARKModule module : catalog.values()) {
            if (isEnabled.test(module.id())) {
                graph.put(module.id(), module.dependencies().stream().map(ModuleDependency::moduleId).toList());
            } else {
                logger.info("Modulo '" + module.id() + "' disabilitato da config");
            }
        }

        for (String id : DependencyResolver.order(graph)) {
            ARKModule module = catalog.get(id);
            try {
                module.onLoad(contextFactory.apply(id));
            } catch (RuntimeException e) {
                throw new ModuleStartupException("onLoad del modulo '" + id + "' fallito: " + e.getMessage(), e);
            }
            loaded.add(module);
        }
    }

    /** Chiama {@code onEnable} nell'ordine di caricamento. Al primo errore si ferma. */
    public void enableAll() {
        for (ARKModule module : loaded) {
            try {
                module.onEnable();
            } catch (RuntimeException e) {
                throw new ModuleStartupException("onEnable del modulo '" + module.id() + "' fallito: " + e.getMessage(), e);
            }
            enabled.add(module);
            logger.info("Modulo '" + module.id() + "' abilitato");
        }
    }

    /** Chiama {@code onDisable} in ordine inverso, solo sui moduli abilitati. Un errore non ferma gli altri. */
    public void disableAll() {
        for (int i = enabled.size() - 1; i >= 0; i--) {
            ARKModule module = enabled.get(i);
            try {
                module.onDisable();
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "onDisable del modulo '" + module.id() + "' fallito", e);
            }
        }
        enabled.clear();
        loaded.clear();
    }

    /** Id dei moduli caricati, in ordine di avvio. */
    public List<String> order() {
        return loaded.stream().map(ARKModule::id).toList();
    }

    /** Stato di ogni modulo del catalogo, in ordine di catalogo. */
    public Map<String, HealthStatus> health() {
        Map<String, HealthStatus> result = new LinkedHashMap<>();
        for (ARKModule module : catalog.values()) {
            HealthStatus status;
            if (enabled.contains(module)) {
                status = safeHealth(module);
            } else if (loaded.contains(module)) {
                status = HealthStatus.STARTING;
            } else {
                status = HealthStatus.DISABLED;
            }
            result.put(module.id(), status);
        }
        return result;
    }

    private HealthStatus safeHealth(ARKModule module) {
        try {
            return Objects.requireNonNullElse(module.health(), HealthStatus.FAILED);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "health() del modulo '" + module.id() + "' ha lanciato un'eccezione", e);
            return HealthStatus.FAILED;
        }
    }
}
