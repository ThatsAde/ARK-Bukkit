package org.breachsmp.core.platform.db;

import com.zaxxer.hikari.HikariConfig;
import org.breachsmp.core.platform.config.DatabaseConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSourceFactoryTest {

    private static final DatabaseConfig DB =
            new DatabaseConfig("db.local", 3306, "breach_game", "bc_game", Path.of("pw"), 0, 1500);

    @Test
    void configFollowsTheSpec() {
        HikariConfig config = DataSourceFactory.hikariConfig(DB, "s3cret", 8);
        assertEquals("breach-db", config.getPoolName());
        assertEquals("org.mariadb.jdbc.Driver", config.getDriverClassName());
        assertEquals("jdbc:mariadb://db.local:3306/breach_game", config.getJdbcUrl());
        assertEquals("bc_game", config.getUsername());
        assertEquals("s3cret", config.getPassword());
        assertEquals(10, config.getMaximumPoolSize());
        assertEquals(10, config.getMinimumIdle());
        assertEquals(1_500_000L, config.getMaxLifetime());
        assertEquals(5_000L, config.getInitializationFailTimeout());
        assertEquals("TRANSACTION_REPEATABLE_READ", config.getTransactionIsolation());
    }

    @Test
    void automaticPoolSizeFollowsCores() {
        assertEquals(4, DataSourceFactory.hikariConfig(DB, "s3cret", 2).getMaximumPoolSize());
    }
}
