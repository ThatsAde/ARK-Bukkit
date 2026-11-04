package org.breachsmp.core.territory.claim;

/** Esito di un tentativo di PIN su un Tool Cupboard (D-37). */
public enum PinResult {
    AUTHORIZED,
    ALREADY_AUTHORIZED,
    WRONG_PIN,
    /** Il claim non ha ancora un PIN: nessuno può autorizzarsi. */
    NO_PIN_SET,
    /** Troppi tentativi negli ultimi 10 minuti, per il giocatore o per il claim: il PIN non viene nemmeno verificato. */
    RATE_LIMITED
}
