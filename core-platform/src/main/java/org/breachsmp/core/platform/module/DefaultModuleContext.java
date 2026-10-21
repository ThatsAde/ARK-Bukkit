package org.breachsmp.core.platform.module;

import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.api.module.ModuleContext;
import org.breachsmp.core.api.service.ServiceRegistry;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Contesto passato a ogni modulo in {@code onLoad}. */
public record DefaultModuleContext(String moduleId, ServiceRegistry services, CoreExecutors executors, Logger logger)
        implements ModuleContext {

    public DefaultModuleContext {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(executors, "executors");
        Objects.requireNonNull(logger, "logger");
    }

    @Override
    public <T> CompletableFuture<T> sync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executors.main());
    }
}
