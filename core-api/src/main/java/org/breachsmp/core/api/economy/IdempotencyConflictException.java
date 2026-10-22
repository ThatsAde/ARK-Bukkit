package org.breachsmp.core.api.economy;

import java.util.UUID;

/** La chiave di idempotenza è già stata usata per un altro tipo di transazione: è un bug del chiamante (E15). */
public final class IdempotencyConflictException extends LedgerException {

    public IdempotencyConflictException(UUID key, String storedKind, String requestedKind) {
        super("Chiave di idempotenza " + key + " già usata per " + storedKind + ", richiesta " + requestedKind);
    }
}
