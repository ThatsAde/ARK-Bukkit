package org.breachsmp.core.api.player;

import java.util.UUID;

/** Chiamato sul main thread quando un giocatore esce: niente I/O. */
@FunctionalInterface
public interface QuitHook {

    void onQuit(UUID uuid);
}
