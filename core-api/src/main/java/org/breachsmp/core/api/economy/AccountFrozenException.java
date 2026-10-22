package org.breachsmp.core.api.economy;

/** Il conto è congelato: non invia né riceve, salvo i tipi admin (E5, E13). */
public final class AccountFrozenException extends LedgerException {

    private final AccountRef account;

    public AccountFrozenException(AccountRef account) {
        super("Conto congelato: " + account);
        this.account = account;
    }

    public AccountRef account() {
        return account;
    }
}
