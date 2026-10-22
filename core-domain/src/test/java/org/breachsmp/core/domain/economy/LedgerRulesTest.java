package org.breachsmp.core.domain.economy;

import org.breachsmp.core.api.economy.AccountFrozenException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.InvalidTransactionException;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerRulesTest {

    private static final AccountRef ALICE = AccountRef.player(new UUID(1, 1));
    private static final AccountRef BOB = AccountRef.player(new UUID(1, 2));
    private static final AccountRef FAUCET = AccountRef.system(SystemAccount.FAUCET);
    private static final AccountRef SINK = AccountRef.system(SystemAccount.SINK);

    private static TxRequest tx(String kind, Leg... legs) {
        return new TxRequest(UUID.randomUUID(), kind, List.of(legs), Map.of(), null);
    }

    @Test
    void validRequestPasses() {
        LedgerRules.validate(tx(TxKinds.PAY, new Leg(ALICE, -10), new Leg(BOB, 10)));
    }

    @Test
    void invalidRequestsAreRejected() {
        assertInvalid("Tipo di transazione sconosciuto: STEAL", tx("STEAL", new Leg(ALICE, -1), new Leg(BOB, 1)));
        assertInvalid("tra 2 e 255 gambe", tx(TxKinds.PAY, new Leg(ALICE, 0)));
        assertInvalid("importo zero", tx(TxKinds.PAY, new Leg(ALICE, 0), new Leg(BOB, 0)));
        assertInvalid("ripetuto", tx(TxKinds.PAY, new Leg(ALICE, -1), new Leg(ALICE, 1)));
        assertInvalid("somma delle gambe", tx(TxKinds.PAY, new Leg(ALICE, -1), new Leg(BOB, 2)));
        assertInvalid("fuori scala", tx(TxKinds.PAY, new Leg(ALICE, Long.MAX_VALUE), new Leg(BOB, Long.MAX_VALUE)));
        assertInvalid("Meta non valida", new TxRequest(UUID.randomUUID(), TxKinds.PAY,
                List.of(new Leg(ALICE, -1), new Leg(BOB, 1)), Map.of("x", 1.5), null));
    }

    private static void assertInvalid(String fragment, TxRequest request) {
        InvalidTransactionException error = assertThrows(InvalidTransactionException.class, () -> LedgerRules.validate(request));
        assertTrue(error.getMessage().contains(fragment), error.getMessage());
    }

    @Test
    void balancesAfterAppliesDeltasInOrder() {
        List<LedgerRules.Posting> postings = List.of(
                new LedgerRules.Posting(new LedgerRules.AccountState(1, ALICE, 100, false), -30),
                new LedgerRules.Posting(new LedgerRules.AccountState(2, BOB, 0, false), 30));
        assertEquals(List.of(70L, 30L), LedgerRules.balancesAfter(postings, false));
    }

    @Test
    void onlyFaucetMayGoNegative() {
        var faucet = new LedgerRules.Posting(new LedgerRules.AccountState(1, FAUCET, 0, false), -50);
        var bob = new LedgerRules.Posting(new LedgerRules.AccountState(2, BOB, 0, false), 50);
        assertEquals(List.of(-50L, 50L), LedgerRules.balancesAfter(List.of(faucet, bob), false));
        var sink = new LedgerRules.Posting(new LedgerRules.AccountState(3, SINK, 10, false), -11);
        var alice = new LedgerRules.Posting(new LedgerRules.AccountState(4, ALICE, 0, false), 11);
        assertEquals(SINK, assertThrows(InsufficientFundsException.class,
                () -> LedgerRules.balancesAfter(List.of(sink, alice), false)).account());
    }

    @Test
    void frozenAccountBlocksUnlessBypassed() {
        var frozenAlice = new LedgerRules.Posting(new LedgerRules.AccountState(1, ALICE, 100, true), -10);
        var bob = new LedgerRules.Posting(new LedgerRules.AccountState(2, BOB, 0, false), 10);
        assertEquals(ALICE, assertThrows(AccountFrozenException.class,
                () -> LedgerRules.balancesAfter(List.of(frozenAlice, bob), false)).account());
        assertEquals(List.of(90L, 10L), LedgerRules.balancesAfter(List.of(frozenAlice, bob), true));
    }
}
