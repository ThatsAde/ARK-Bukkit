package org.breachsmp.core.platform.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Applica le migrazioni Flyway di {@code db/migration} (spec §4). */
public final class Migrator {

    static final String LOCATION = "classpath:db/migration";

    private final DataSource dataSource;
    private final ClassLoader classLoader;

    /** @param classLoader il classloader del plugin: Flyway deve cercare le migrazioni dentro il jar di BreachCore */
    public Migrator(DataSource dataSource, ClassLoader classLoader) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /**
     * @return numero di migrazioni eseguite ora (0 se lo schema era già aggiornato)
     * @throws SchemaTooNewException se il DB ha migrazioni che questo jar non conosce
     */
    public int migrate() {
        Flyway flyway = Flyway.configure(classLoader)
                .dataSource(dataSource)
                .locations(LOCATION)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();

        List<String> unknown = Arrays.stream(flyway.info().all())
                .filter(info -> info.getState() == MigrationState.FUTURE_SUCCESS
                        || info.getState() == MigrationState.FUTURE_FAILED)
                .map(info -> "V" + info.getVersion() + " " + info.getDescription())
                .toList();
        if (!unknown.isEmpty()) {
            throw new SchemaTooNewException(unknown);
        }
        return flyway.migrate().migrationsExecuted;
    }
}
