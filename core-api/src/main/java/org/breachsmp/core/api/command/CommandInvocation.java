package org.breachsmp.core.api.command;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Chi ha eseguito un comando e con quali argomenti.
 *
 * @param senderId           UUID del giocatore; {@code null} = console
 * @param grantedPermissions i permessi extra dichiarati dal comando che il mittente possiede
 */
public record CommandInvocation(UUID senderId, String senderName, List<String> args, Set<String> grantedPermissions) {

    public CommandInvocation {
        Objects.requireNonNull(senderName, "senderName");
        args = List.copyOf(args);
        grantedPermissions = Set.copyOf(grantedPermissions);
    }

    public boolean isPlayer() {
        return senderId != null;
    }

    public boolean has(String permission) {
        return grantedPermissions.contains(permission);
    }
}
