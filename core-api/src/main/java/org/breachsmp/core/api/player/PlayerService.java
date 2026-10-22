package org.breachsmp.core.api.player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Profili dei giocatori già entrati almeno una volta (E16). Le query girano sul thread DB. */
public interface PlayerService {

    /** Giocatore con questo nome, senza distinzione di maiuscole; se più UUID hanno avuto il nome, quello visto più di recente. */
    CompletableFuture<Optional<PlayerProfile>> findByName(String name);

    CompletableFuture<Optional<PlayerProfile>> find(UUID uuid);
}
