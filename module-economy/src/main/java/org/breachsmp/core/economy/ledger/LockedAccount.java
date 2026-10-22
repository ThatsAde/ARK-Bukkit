package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.domain.economy.LedgerRules;

/** Conto bloccato con {@code FOR UPDATE}: stato per le regole pure + {@code version} per la cache (D-29). */
public record LockedAccount(LedgerRules.AccountState state, long version) {
}
