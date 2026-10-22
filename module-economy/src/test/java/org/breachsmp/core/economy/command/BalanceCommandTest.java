package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.Tone;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.player.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceCommandTest {

    private final Fakes.FakeLedger ledger = new Fakes.FakeLedger();
    private final Fakes.FakePlayers players = new Fakes.FakePlayers();
    private final BalanceCommand command = new BalanceCommand(ledger, players);

    @Test
    void ownBalanceForPlayersOnly() throws Exception {
        UUID alice = UUID.randomUUID();
        ledger.balances.put(AccountRef.player(alice), 123_456L);
        assertEquals(ChatLine.info("Saldo: 1.234,56 monete"),
                command.execute(Fakes.invocation(alice, "Alice", Set.of())).get(5, SECONDS).lines().getFirst());
        assertEquals("Dalla console usa /balance <giocatore>.",
                command.execute(Fakes.invocation(null, "CONSOLE", Set.of())).get(5, SECONDS).lines().getFirst().text());
    }

    @Test
    void othersNeedThePermission() throws Exception {
        PlayerProfile bob = new PlayerProfile(UUID.randomUUID(), "Bob", Instant.EPOCH, Instant.EPOCH);
        players.add(bob);
        ledger.balances.put(AccountRef.player(bob.uuid()), 100L);
        assertEquals("Non hai il permesso di vedere il saldo di altri giocatori.",
                command.execute(Fakes.invocation(UUID.randomUUID(), "Alice", Set.of(), "Bob")).get(5, SECONDS).lines().getFirst().text());
        assertEquals("Saldo di Bob: 1 moneta",
                command.execute(Fakes.invocation(null, "CONSOLE", Set.of(BalanceCommand.PERMISSION_OTHERS), "bob")).get(5, SECONDS)
                        .lines().getFirst().text());
        assertEquals(Tone.ERROR, command.execute(Fakes.invocation(null, "CONSOLE", Set.of(BalanceCommand.PERMISSION_OTHERS), "Nessuno"))
                .get(5, SECONDS).lines().getFirst().tone());
    }
}
