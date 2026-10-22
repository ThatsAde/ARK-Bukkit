package org.breachsmp.core.api.economy;

/** Evento pubblicato dopo il COMMIT di una transazione nuova (non per i replay), sul thread DB. */
public record LedgerTxCommitted(TxResult result) {
}
