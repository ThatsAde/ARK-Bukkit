package org.breachsmp.core.api.economy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Riferimento a un conto della stagione corrente: tipo di proprietario + UUID (per SYSTEM il ref fisso di {@link SystemAccount}). */
public record AccountRef(OwnerType type, UUID owner) {

    public AccountRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
        if (type == OwnerType.SYSTEM && SystemAccount.byRef(owner).isEmpty()) {
            throw new IllegalArgumentException("Conto di sistema sconosciuto: " + owner);
        }
    }

    public static AccountRef player(UUID uuid) {
        return new AccountRef(OwnerType.PLAYER, uuid);
    }

    public static AccountRef system(SystemAccount account) {
        return new AccountRef(OwnerType.SYSTEM, account.ref());
    }

    public Optional<SystemAccount> systemAccount() {
        return type == OwnerType.SYSTEM ? SystemAccount.byRef(owner) : Optional.empty();
    }

    /** Solo {@link SystemAccount#FAUCET} può avere saldo negativo (spec §5). */
    public boolean mayGoNegative() {
        return systemAccount().map(SystemAccount::mayGoNegative).orElse(false);
    }

    @Override
    public String toString() {
        return switch (type) {
            case PLAYER -> "giocatore:" + owner;
            case TEAM -> "team:" + owner;
            case SYSTEM -> "sistema:" + systemAccount().orElseThrow().name();
        };
    }
}
