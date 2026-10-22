package org.breachsmp.core.api.economy;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Conti di sistema (spec §5) con {@code owner_ref} fisso = {@code new UUID(0, n)} (D-19). Solo FAUCET può andare in negativo. */
public enum SystemAccount {
    FAUCET(1, true),
    SINK(2, false),
    TAX_POOL(3, false),
    ESCROW(4, false);

    private final UUID ref;
    private final boolean mayGoNegative;

    SystemAccount(long id, boolean mayGoNegative) {
        this.ref = new UUID(0L, id);
        this.mayGoNegative = mayGoNegative;
    }

    public UUID ref() {
        return ref;
    }

    public boolean mayGoNegative() {
        return mayGoNegative;
    }

    public static Optional<SystemAccount> byRef(UUID ref) {
        return Arrays.stream(values()).filter(account -> account.ref.equals(ref)).findFirst();
    }
}
