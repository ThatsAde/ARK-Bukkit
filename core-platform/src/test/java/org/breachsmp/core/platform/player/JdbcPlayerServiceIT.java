package org.breachsmp.core.platform.player;

import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.platform.testkit.MutableClock;
import org.breachsmp.core.platform.testkit.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPlayerServiceIT {

    private HikariDataSource dataSource;
    private ExecutorService db;
    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 22, 12, 0));
    private JdbcPlayerService players;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        db = Executors.newFixedThreadPool(2);
        players = new JdbcPlayerService(dataSource, db, clock);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void upsertCreatesThenUpdatesNameAndLastSeen() throws Exception {
        UUID alice = UUID.randomUUID();
        players.upsert(alice, "Alice");
        clock.advance(Duration.ofHours(1));
        players.upsert(alice, "Alice2");
        PlayerProfile profile = players.find(alice).get(5, SECONDS).orElseThrow();
        assertEquals("Alice2", profile.name());
        assertEquals(Duration.ofHours(1), Duration.between(profile.firstSeen(), profile.lastSeen()));
    }

    @Test
    void findByNameIsCaseInsensitiveAndPrefersMostRecent() throws Exception {
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        players.upsert(old, "Steve");
        clock.advance(Duration.ofDays(1));
        players.upsert(recent, "Steve");
        assertEquals(recent, players.findByName("sTeVe").get(5, SECONDS).orElseThrow().uuid());
        assertTrue(players.findByName("Nessuno").get(5, SECONDS).isEmpty());
        assertTrue(players.find(UUID.randomUUID()).get(5, SECONDS).isEmpty());
    }
}
