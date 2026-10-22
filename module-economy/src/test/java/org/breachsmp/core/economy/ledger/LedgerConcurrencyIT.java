package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.economy.LedgerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DoD di M1 (spec §17): 1000 transazioni concorrenti su MariaDB reale senza violare le invarianti. */
class LedgerConcurrencyIT {

    private static final AccountRef FAUCET = AccountRef.system(SystemAccount.FAUCET);
    private static final AccountRef SINK = AccountRef.system(SystemAccount.SINK);

    private LedgerFixture f;

    @BeforeEach
    void setUp() {
        f = new LedgerFixture();
    }

    @AfterEach
    void tearDown() {
        if (f != null) {
            f.close();
        }
    }

    @Test
    void thousandConcurrentTransactionsKeepTheInvariants() throws Exception {
        List<UUID> players = IntStream.range(0, 20).mapToObj(i -> UUID.randomUUID()).toList();
        for (UUID player : players) {
            f.give(player, 100_000);
        }
        Random random = new Random(42);
        List<TxRequest> requests = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            int roll = random.nextInt(100);
            AccountRef a = AccountRef.player(players.get(random.nextInt(players.size())));
            long amount = 1 + random.nextInt(30_000);
            if (roll < 70) {
                AccountRef b;
                do {
                    b = AccountRef.player(players.get(random.nextInt(players.size())));
                } while (b.equals(a));
                requests.add(f.tx(TxKinds.PAY, new Leg(a, -amount), new Leg(b, amount)));
            } else if (roll < 85) {
                requests.add(f.tx(TxKinds.ADMIN_TAKE, new Leg(a, -amount), new Leg(SINK, amount)));
            } else {
                requests.add(f.tx(TxKinds.ADMIN_GIVE, new Leg(FAUCET, -amount), new Leg(a, amount)));
            }
        }
        List<TxRequest> submitted = new ArrayList<>(requests);
        submitted.addAll(requests.subList(0, 100)); // doppi invii con la stessa chiave, in parallelo
        Collections.shuffle(submitted, random);

        List<CompletableFuture<TxResult>> futures = submitted.stream().map(f.ledger::execute).toList();
        Map<UUID, Long> txIdByKey = new ConcurrentHashMap<>();
        int insufficient = 0;
        for (CompletableFuture<TxResult> future : futures) {
            try {
                TxResult result = future.get(120, SECONDS);
                Long previous = txIdByKey.putIfAbsent(result.idemKey(), result.txId());
                if (previous != null) {
                    assertEquals(previous, result.txId(), "stessa chiave → stessa transazione");
                }
            } catch (ExecutionException e) {
                assertInstanceOf(InsufficientFundsException.class, e.getCause(), "unico errore ammesso: saldo insufficiente");
                insufficient++;
            }
        }

        assertEquals(20 + txIdByKey.size(), f.count("SELECT COUNT(*) FROM ledger_tx"), "una riga per chiave riuscita");
        assertEquals(0, f.count("SELECT COALESCE(SUM(balance), 0) FROM account"), "partita doppia");
        assertEquals(0, f.count("SELECT COUNT(*) FROM account WHERE owner_type = 'PLAYER' AND balance < 0"));
        assertTrue(f.dbBalance(SINK) >= 0);
        assertEquals(0, f.count("""
                SELECT COUNT(*) FROM (
                  SELECT a.id FROM account a LEFT JOIN ledger_entry e ON e.account_id = a.id
                  GROUP BY a.id, a.balance HAVING a.balance <> COALESCE(SUM(e.delta), 0)) x"""), "saldo = somma delle entry");
        assertEquals(0, f.count("SELECT COUNT(*) FROM (SELECT tx_id FROM ledger_entry GROUP BY tx_id HAVING SUM(delta) <> 0) x"));
        Logger.getLogger("LedgerConcurrencyIT").info("Riuscite: " + txIdByKey.size() + ", saldo insufficiente: " + insufficient
                + ", retry deadlock: " + f.ledger.deadlockRetries());
    }
}
