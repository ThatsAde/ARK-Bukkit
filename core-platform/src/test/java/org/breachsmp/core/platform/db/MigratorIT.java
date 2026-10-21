package org.breachsmp.core.platform.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MigratorIT {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4");

    private HikariDataSource dataSource;
    private Migrator migrator;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MARIADB.getJdbcUrl());
        config.setUsername(MARIADB.getUsername());
        config.setPassword(MARIADB.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        migrator = new Migrator(dataSource, getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void appliesMigrationsOnEmptySchema() throws SQLException {
        assertTrue(migrator.migrate() >= 1);
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "player", null)) {
            assertTrue(tables.next(), "la tabella player deve esistere");
        }
    }

    @Test
    void secondRunAppliesNothing() {
        migrator.migrate();
        assertEquals(0, migrator.migrate());
    }

    @Test
    void refusesSchemaNewerThanCode() throws SQLException {
        migrator.migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO flyway_schema_history
                      (installed_rank, version, description, type, script, checksum,
                       installed_by, installed_on, execution_time, success)
                    VALUES (999, '999', 'from the future', 'SQL', 'V999__from_the_future.sql', 0,
                            'test', NOW(), 0, 1)
                    """);
        }
        assertThrows(SchemaTooNewException.class, migrator::migrate);
    }
}
