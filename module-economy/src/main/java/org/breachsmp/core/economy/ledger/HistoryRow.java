package org.breachsmp.core.economy.ledger;

import java.time.Instant;

/** Un movimento di un conto per {@code /breach tx}. */
public record HistoryRow(long txId, Instant createdAt, String kind, long delta, long balanceAfter) {
}
