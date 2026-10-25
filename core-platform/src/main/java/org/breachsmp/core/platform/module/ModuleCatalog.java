package org.breachsmp.core.platform.module;

import org.breachsmp.core.api.module.ARKModule;
import org.breachsmp.core.economy.EconomyModule;

import java.util.List;

/**
 * Unico punto che conosce le implementazioni dei moduli (invariante I7).
 * Ogni nuovo {@code module-*} si aggiunge qui, con la sua chiave {@code modules.<id>.enabled} in config.
 */
public final class ModuleCatalog {

    private ModuleCatalog() {
    }

    public static List<ARKModule> all() {
        return List.of(new EconomyModule());
    }
}
