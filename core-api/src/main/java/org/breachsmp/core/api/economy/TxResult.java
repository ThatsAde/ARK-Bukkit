package org.breachsmp.core.api.economy;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Esito di una transazione committata.
 *
 * @param replayed {@code true} se la chiave di idempotenza esisteva già e questo è il risultato salvato
 */
public record TxResult(long txId, UUID idemKey, String kind, Instant createdAt, List<EntryView> entries, boolean replayed) {

    public TxResult {
        entries = List.copyOf(entries);
    }

    public OptionalLong balanceAfter(AccountRef account) {
        return entries.stream()
                .filter(entry -> entry.account().equals(account))
                .mapToLong(EntryView::balanceAfter)
                .findFirst();
    }
}
