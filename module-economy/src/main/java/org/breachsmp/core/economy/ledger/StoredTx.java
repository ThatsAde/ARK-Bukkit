package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.api.economy.EntryView;
import org.breachsmp.core.api.economy.TxResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Una transazione già salvata, con le sue righe in ordine di {@code seq}. */
public record StoredTx(long id, int seasonId, UUID idemKey, String kind, Instant createdAt, List<EntryView> entries) {

    public StoredTx {
        entries = List.copyOf(entries);
    }

    public TxResult toResult(boolean replayed) {
        return new TxResult(id, idemKey, kind, createdAt, entries, replayed);
    }
}
