package org.breachsmp.core.platform.db;

import java.util.List;

/** Il database contiene migrazioni che questo jar non conosce: il plugin rifiuta di partire (spec §4). */
public final class SchemaTooNewException extends RuntimeException {

    public SchemaTooNewException(List<String> unknownMigrations) {
        super("Lo schema del database è più recente di questo plugin (migrazioni sconosciute: "
                + String.join(", ", unknownMigrations) + "). Aggiorna il plugin: non si avvia su uno schema più nuovo.");
    }
}
