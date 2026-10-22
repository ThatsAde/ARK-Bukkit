package org.breachsmp.core.api.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Chiavi di idempotenza deterministiche per operazioni ripetibili (job, rollback): stesso testo → stessa chiave (E15). */
public final class IdemKeys {

    private IdemKeys() {
    }

    public static UUID of(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Il testo della chiave di idempotenza non può essere vuoto");
        }
        return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
