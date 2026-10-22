package org.breachsmp.core.platform.season;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentSeasonIT {

    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void loadsSeasonOneFromTheMigration() {
        assertEquals(1, CurrentSeason.load(dataSource).currentSeasonId());
    }

    @Test
    void emptyTableFailsClosed() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM current_season");
        }
        assertThrows(IllegalStateException.class, () -> CurrentSeason.load(dataSource));
    }
}
