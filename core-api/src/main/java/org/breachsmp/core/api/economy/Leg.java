package org.breachsmp.core.api.economy;

import java.util.Objects;

/** Una gamba di una transazione: variazione (in unità minime) del saldo di un conto. */
public record Leg(AccountRef account, long delta) {

    public Leg {
        Objects.requireNonNull(account, "account");
    }
}
