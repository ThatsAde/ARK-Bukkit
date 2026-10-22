package org.breachsmp.core.api.economy;

/** Errore del ledger: la transazione non è stata eseguita, salvo dove indicato (es. errore durante il commit). */
public class LedgerException extends RuntimeException {

    public LedgerException(String message) {
        super(message);
    }

    public LedgerException(String message, Throwable cause) {
        super(message, cause);
    }
}
