package org.breachsmp.core.api.command;

import java.util.Objects;
import java.util.UUID;

/** Messaggio per un altro giocatore (es. il destinatario di {@code /pay}); consegnato solo se è online. */
public record Notice(UUID player, ChatLine line) {

    public Notice {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(line, "line");
    }
}
