package org.breachsmp.core.platform.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.platform.config.DatabaseConfig;

import java.util.concurrent.TimeUnit;

/** Pool HikariCP configurato secondo la spec §4. */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static HikariConfig hikariConfig(DatabaseConfig db, String password, int cores) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("breach-db");
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(db.jdbcUrl());
        config.setUsername(db.user());
        config.setPassword(password);
        int size = db.effectivePoolSize(cores);
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(size);
        config.setMaxLifetime(TimeUnit.SECONDS.toMillis(db.maxLifetimeSeconds()));
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(5));
        // > 0: il pool si connette subito e fa fallire l'avvio se il DB non risponde (fail-closed, D-08)
        config.setInitializationFailTimeout(TimeUnit.SECONDS.toMillis(5));
        config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("cachePrepStmts", "true");
        return config;
    }

    /** Apre il pool. Lancia un'eccezione se il DB non è raggiungibile entro 5 secondi. */
    public static HikariDataSource create(DatabaseConfig db, String password, int cores) {
        return new HikariDataSource(hikariConfig(db, password, cores));
    }
}
