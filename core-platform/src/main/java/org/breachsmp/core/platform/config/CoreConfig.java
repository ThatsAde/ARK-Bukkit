package org.breachsmp.core.platform.config;

import java.util.Map;

/** Configurazione validata di BreachCore ({@code config.yml}). */
public record CoreConfig(int configVersion, DatabaseConfig database, Map<String, Boolean> modules) {

    public CoreConfig {
        modules = Map.copyOf(modules);
    }

    /** Feature flag {@code modules.<id>.enabled}; un modulo non configurato è disabilitato (D-08). */
    public boolean moduleEnabled(String id) {
        return modules.getOrDefault(id, false);
    }
}
