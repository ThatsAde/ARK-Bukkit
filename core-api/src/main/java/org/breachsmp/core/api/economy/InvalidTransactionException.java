package org.breachsmp.core.api.economy;

/** Richiesta malformata (tipo sconosciuto, somma diversa da zero, conto ripetuto, …): non tocca il database. */
public final class InvalidTransactionException extends LedgerException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
