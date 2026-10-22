package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.Notice;
import org.breachsmp.core.api.command.Tone;
import org.breachsmp.core.api.economy.AccountFrozenException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.LedgerException;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.domain.ratelimit.SlidingWindowLimiter;
import org.breachsmp.core.economy.EconomyTunables;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PayCommandTest {

    private final Fakes.FakeLedger ledger = new Fakes.FakeLedger();
    private final Fakes.FakePlayers players = new Fakes.FakePlayers();
    private final UUID alice = UUID.randomUUID();
    private final PlayerProfile bob = new PlayerProfile(UUID.randomUUID(), "Bob", Instant.EPOCH, Instant.EPOCH);

    private PayCommand command(long feeBp, long ratePerMinute) {
        EconomyTunables tunables = new EconomyTunables(Fakes.tunable("economy.starting_balance", 0),
                Fakes.tunable("economy.transfer_fee_bp", feeBp), Fakes.tunable("economy.pay_rate_per_minute", ratePerMinute),
                Fakes.tunable("economy.recon_hour", 4));
        return new PayCommand(ledger, players, Fakes.DIRECT_LOCK, tunables,
                new SlidingWindowLimiter(Clock.systemUTC(), Duration.ofMinutes(1)));
    }

    @Test
    void paysAndNotifiesTheRecipient() throws Exception {
        players.add(bob);
        CommandResult result = command(0, 10).execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "12,50")).get(5, SECONDS);
        TxRequest request = ledger.requests.getFirst();
        assertEquals(TxKinds.PAY, request.kind());
        assertEquals(alice, request.actor());
        assertEquals(List.of(new Leg(AccountRef.player(alice), -1250), new Leg(AccountRef.player(bob.uuid()), 1250)), request.legs());
        assertEquals(List.of(ChatLine.success("Hai inviato 12,50 monete a Bob.")), result.lines());
        assertEquals(List.of(new Notice(bob.uuid(), ChatLine.success("Hai ricevuto 12,50 monete da Alice."))), result.notices());
    }

    @Test
    void feeGoesToTheTaxPool() throws Exception {
        players.add(bob);
        CommandResult result = command(500, 10).execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "100")).get(5, SECONDS);
        assertEquals(List.of(new Leg(AccountRef.player(alice), -10_000), new Leg(AccountRef.player(bob.uuid()), 9_500),
                new Leg(AccountRef.system(SystemAccount.TAX_POOL), 500)), ledger.requests.getFirst().legs());
        assertEquals(ChatLine.muted("Tassa trattenuta: 5 monete; Bob riceve 95 monete."), result.lines().get(1));
        assertEquals(ChatLine.success("Hai ricevuto 95 monete da Alice."), result.notices().getFirst().line());
    }

    @Test
    void rejectsBadInputWithoutTouchingTheLedger() throws Exception {
        players.add(bob);
        PayCommand pay = command(0, 10);
        for (String[] args : List.of(new String[]{}, new String[]{"Bob"}, new String[]{"Bob", "0"}, new String[]{"Bob", "abc"},
                new String[]{"Bob", "0,50"}, new String[]{"Bob", "1", "extra"})) {
            CommandResult result = pay.execute(Fakes.invocation(alice, "Alice", Set.of(), args)).get(5, SECONDS);
            assertEquals(Tone.ERROR, result.lines().getFirst().tone(), String.join(" ", args));
        }
        assertEquals("Il pagamento minimo è 1 moneta.",
                pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "0,50")).get(5, SECONDS).lines().getFirst().text());
        assertEquals(List.of(), ledger.requests);
    }

    @Test
    void unknownTargetSelfPaymentAndCaseInsensitiveName() throws Exception {
        players.add(bob);
        players.add(new PlayerProfile(alice, "Alice", Instant.EPOCH, Instant.EPOCH));
        PayCommand pay = command(0, 10);
        assertEquals("Giocatore non trovato: Nessuno (deve essere entrato almeno una volta).",
                pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "Nessuno", "1")).get(5, SECONDS).lines().getFirst().text());
        assertEquals("Non puoi pagare te stesso.",
                pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "alice", "1")).get(5, SECONDS).lines().getFirst().text());
        assertEquals(Tone.SUCCESS, pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "bOb", "1")).get(5, SECONDS).lines().getFirst().tone());
    }

    @Test
    void rateLimitBlocksAfterTheLimit() throws Exception {
        players.add(bob);
        PayCommand pay = command(0, 2);
        pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "1")).get(5, SECONDS);
        pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "1")).get(5, SECONDS);
        assertEquals("Troppi pagamenti in poco tempo: riprova tra un minuto.",
                pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "1")).get(5, SECONDS).lines().getFirst().text());
        assertEquals(2, ledger.requests.size());
    }

    @Test
    void failureMessagesAreSpecific() throws Exception {
        players.add(bob);
        PayCommand pay = command(0, 100);
        ledger.nextFailure = new InsufficientFundsException(AccountRef.player(alice));
        assertEquals("Saldo insufficiente.", text(pay));
        ledger.nextFailure = new AccountFrozenException(AccountRef.player(bob.uuid()));
        assertEquals("Il conto di destinazione è congelato.", text(pay));
        ledger.nextFailure = new AccountFrozenException(AccountRef.player(alice));
        assertEquals("Il tuo conto è congelato: i trasferimenti sono bloccati.", text(pay));
        ledger.nextFailure = new LedgerException("connessione persa durante il commit");
        assertEquals("Esito incerto per un errore del server: controlla il saldo con /balance prima di riprovare.", text(pay));
    }

    private String text(PayCommand pay) throws Exception {
        return pay.execute(Fakes.invocation(alice, "Alice", Set.of(), "Bob", "1")).get(5, SECONDS).lines().getFirst().text();
    }
}
