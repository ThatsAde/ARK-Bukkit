package org.breachsmp.core.api.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Giocatore conosciuto dal server (tabella globale {@code player}). */
public record PlayerProfile(UUID uuid, String name, Instant firstSeen, Instant lastSeen) {

    public PlayerProfile {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
    }
}
