package org.breachsmp.core.api.economy;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * L'unico punto da cui passa il denaro (I3, spec §5). Algoritmo (D-20):
 * 1. validazione pura (somma zero, conti distinti, tipo noto);
 * 2. conti risolti o creati in autocommit;
 * 3. {@code INSERT ledger_tx}: chiave già usata → risultato salvato (stesso tipo) o {@link IdempotencyConflictException};
 * 4. {@code SELECT … FOR UPDATE} dei conti in ordine di id;
 * 5. controlli di saldo e freeze, {@code UPDATE} dei saldi e {@code INSERT ledger_entry};
 * 6. {@code COMMIT}, poi cache ed evento {@link LedgerTxCommitted}. Retry fino a 3 volte su deadlock.
 */
public interface LedgerService {

    /** Esegue sul thread DB. Errori attesi: {@link InsufficientFundsException}, {@link AccountFrozenException}, {@link InvalidTransactionException}. */
    CompletableFuture<TxResult> execute(TxRequest request);

    /** Saldo del conto nella stagione corrente; conto inesistente = 0. */
    CompletableFuture<Long> balance(AccountRef account);

    /** Chiamato dopo ogni COMMIT di una transazione nuova, sul thread DB: deve essere veloce e non lanciare. */
    void subscribe(Consumer<LedgerTxCommitted> listener);
}
