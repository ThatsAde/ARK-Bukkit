package org.breachsmp.core.api.economy;

/** Una riga di {@code ledger_entry} vista dal chiamante: conto, variazione e saldo dopo la transazione. */
public record EntryView(AccountRef account, long delta, long balanceAfter) {
}
