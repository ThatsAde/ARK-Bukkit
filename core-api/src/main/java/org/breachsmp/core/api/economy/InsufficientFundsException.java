package org.breachsmp.core.api.economy;

/** Un conto che non può andare in negativo finirebbe sotto zero. */
public final class InsufficientFundsException extends LedgerException {

    private final AccountRef account;

    public InsufficientFundsException(AccountRef account) {
        super("Saldo insufficiente sul conto " + account);
        this.account = account;
    }

    public AccountRef account() {
        return account;
    }
}
