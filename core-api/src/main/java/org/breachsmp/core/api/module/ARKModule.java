package org.breachsmp.core.api.module;

import java.util.Collection;

public interface ARKModule {

    String id();

    void onLoad(ModuleContext context);

    void onEnable();

    void onDisable();

    HealthStatus health();

    Collection<ModuleDependency> dependencies();
}
