package org.breachsmp.core.api.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Richiesta di transazione (spec §5).
 *
 * @param idemKey chiave di idempotenza (I4): la stessa chiave restituisce il risultato già salvato
 * @param kind    uno dei {@link TxKinds}
 * @param legs    gambe a somma zero (partita doppia), conti distinti
 * @param meta    dettagli in JSON (solo testo, interi, booleani); {@code null} = nessuno
 * @param actor   chi ha causato la transazione; {@code null} = console o sistema
 */
public record TxRequest(UUID idemKey, String kind, List<Leg> legs, Map<String, Object> meta, UUID actor) {

    public TxRequest {
        Objects.requireNonNull(idemKey, "idemKey");
        Objects.requireNonNull(kind, "kind");
        legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
        meta = meta == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }
}
