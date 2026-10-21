package org.breachsmp.core.platform.config;

import java.nio.file.Path;

/**
 * Impostazioni del database. La password non sta qui: si legge da {@code passwordFile} solo al momento della connessione (D-09).
 *
 * @param passwordFile percorso assoluto del file che contiene solo la password
 * @param poolSize     dimensione del pool; {@code 0} = automatica
 */
public record DatabaseConfig(String host, int port, String name, String user, Path passwordFile,
                             int poolSize, int maxLifetimeSeconds) {

    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + name;
    }

    /** {@code poolSize} se impostato, altrimenti {@code min(10, core*2)} (spec §4). */
    public int effectivePoolSize(int cores) {
        return poolSize > 0 ? poolSize : Math.min(10, Math.max(1, cores) * 2);
    }
}
