package org.breachsmp.core.api.lock;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Serializza le operazioni critiche di uno stesso giocatore (spec §15): {@code action} parte quando l'azione precedente
 * dello stesso giocatore è finita. {@code action} deve restituire subito un future (niente blocchi).
 */
public interface PlayerLock {

    <T> CompletableFuture<T> withLock(UUID player, Supplier<CompletableFuture<T>> action);
}
