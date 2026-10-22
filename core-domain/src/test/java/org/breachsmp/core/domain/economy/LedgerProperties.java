package org.breachsmp.core.domain.economy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.InvalidTransactionException;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test di proprietà della DoD di M1 (spec §5, §16): il denaro non si crea né si distrugge, e solo FAUCET va in negativo. */
class LedgerProperties {

    private static final List<AccountRef> PLAYERS = IntStream.range(0, 4)
            .mapToObj(i -> AccountRef.player(new UUID(1, i))).toList();
    private static final AccountRef FAUCET = AccountRef.system(SystemAccount.FAUCET);
    private static final AccountRef SINK = AccountRef.system(SystemAccount.SINK);
    private static final AccountRef TAX = AccountRef.system(SystemAccount.TAX_POOL);

    record Op(int kind, int from, int to, long amount, long feeBp) {
    }

    @Provide
    Arbitrary<List<Op>> operations() {
        return Combinators.combine(
                        Arbitraries.integers().between(0, 2),
                        Arbitraries.integers().between(0, 3),
                        Arbitraries.integers().between(0, 3),
                        Arbitraries.longs().between(1, 1_000_000),
                        Arbitraries.longs().between(0, 5_000))
                .as(Op::new).list().ofMaxSize(200);
    }

    @Property(tries = 300)
    void moneyIsNeitherCreatedNorDestroyedAndOnlyFaucetGoesNegative(@ForAll("operations") List<Op> ops) {
        InMemoryLedger ledger = new InMemoryLedger();
        for (Op op : ops) {
            TxRequest request = switch (op.kind()) {
                case 0 -> tx(TxKinds.ADMIN_GIVE, new Leg(FAUCET, -op.amount()), new Leg(PLAYERS.get(op.to()), op.amount()));
                case 1 -> pay(op);
                default -> tx(TxKinds.ADMIN_TAKE, new Leg(PLAYERS.get(op.from()), -op.amount()), new Leg(SINK, op.amount()));
            };
            Map<AccountRef, Long> before = ledger.snapshot();
            try {
                ledger.apply(request);
            } catch (InsufficientFundsException | InvalidTransactionException e) {
                assertEquals(before, ledger.snapshot(), "una transazione rifiutata non deve cambiare nulla");
            }
        }
        Map<AccountRef, Long> balances = ledger.snapshot();
        assertEquals(0L, balances.values().stream().mapToLong(Long::longValue).sum(), "partita doppia: la somma è sempre 0");
        balances.forEach((ref, balance) -> assertTrue(balance >= 0 || ref.mayGoNegative(), ref + " = " + balance));
    }

    private static TxRequest pay(Op op) {
        FeePolicy.Split split = FeePolicy.split(op.amount(), op.feeBp());
        List<Leg> legs = new ArrayList<>();
        legs.add(new Leg(PLAYERS.get(op.from()), -op.amount()));
        legs.add(new Leg(PLAYERS.get(op.to()), split.net()));
        if (split.fee() > 0) {
            legs.add(new Leg(TAX, split.fee()));
        }
        return new TxRequest(UUID.randomUUID(), TxKinds.PAY, legs, Map.of(), null);
    }

    private static TxRequest tx(String kind, Leg... legs) {
        return new TxRequest(UUID.randomUUID(), kind, List.of(legs), Map.of(), null);
    }

    @Property
    void feeIsTheCeilingOfTheExactTax(@ForAll @LongRange(min = 1, max = 1_000_000_000_000L) long amount,
                                      @ForAll @LongRange(min = 1, max = 10_000) long basisPoints) {
        long fee = FeePolicy.fee(amount, basisPoints);
        assertTrue(fee >= 1 && fee <= amount);
        assertTrue(fee * 10_000 >= amount * basisPoints);
        assertTrue((fee - 1) * 10_000 < amount * basisPoints);
    }

    @Property
    void parseReadsWhatAPlainAmountSays(@ForAll @LongRange(min = 1, max = 99_999_999_999_999_99L) long units) {
        String plain = (units / 100) + "," + String.format("%02d", units % 100);
        assertEquals(units, Money.parse(plain));
        assertTrue(Money.format(units).endsWith(units == 100 ? " moneta" : " monete"));
    }
}
