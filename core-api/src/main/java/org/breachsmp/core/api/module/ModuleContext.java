package org.breachsmp.core.api.module;

import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.api.service.ServiceRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Ciò che la piattaforma offre a un modulo in {@link ARKModule#onLoad}. */
public interface ModuleContext {

    /** Id del modulo che riceve questo contesto. */
    String moduleId();

    ServiceRegistry services();

    CoreExecutors executors();

    Logger logger();

    /** Esegue {@code task} sul main thread e completa il future con il risultato: l'unico modo per tornare sul main (I2). */
    <T> CompletableFuture<T> sync(Supplier<T> task);
}
