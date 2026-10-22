package org.breachsmp.core.economy;

import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.domain.ratelimit.SlidingWindowLimiter;
import org.breachsmp.core.platform.tunable.TunableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyLoginIT {

    private LedgerFixture f;
    private TunableRegistry tunableRegistry;
    private EconomyLogin login;

    @BeforeEach
    void setUp() {
        f = new LedgerFixture();
        tunableRegistry = new TunableRegistry(f.dataSource, f.db, f.clock, Logger.getLogger("test"));
        EconomyTunables tunables = EconomyTunables.register(tunableRegistry);
        login = new EconomyLogin(f.seasons, f.accounts, f.ledger, tunables, f.cache,
                new SlidingWindowLimiter(f.clock, Duration.ofMinutes(1)));
    }

    @AfterEach
    void tearDown() {
        if (f != null) {
            f.close();
        }
    }

    @Test
    void loginCreatesTheAccountAndPreloadsTheCacheWithoutStartingBalanceByDefault() throws Exception {
        UUID alice = UUID.randomUUID();
        login.onLogin(alice, "Alice");
        assertEquals(1, f.count("SELECT COUNT(*) FROM account WHERE owner_type = 'PLAYER'"));
        assertEquals(0, f.count("SELECT COUNT(*) FROM ledger_tx"));
        assertEquals(OptionalLong.of(0), f.cache.get(AccountRef.player(alice)));
        login.onQuit(alice);
        assertEquals(OptionalLong.empty(), f.cache.get(AccountRef.player(alice)));
    }

    @Test
    void startingBalanceIsGrantedOncePerSeason() throws Exception {
        tunableRegistry.set("economy.starting_balance", "500", null).get(5, SECONDS);
        UUID alice = UUID.randomUUID();
        login.onLogin(alice, "Alice");
        login.onLogin(alice, "Alice");
        assertEquals(500, f.dbBalance(AccountRef.player(alice)));
        assertEquals(1, f.count("SELECT COUNT(*) FROM ledger_tx WHERE kind = 'STARTING_BALANCE'"));
    }
}
