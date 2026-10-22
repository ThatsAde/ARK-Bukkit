package org.breachsmp.core.platform.lock;

import org.breachsmp.core.api.lock.PlayerLock;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** {@link PlayerLock} in memoria: per ogni giocatore le azioni partono una dopo l'altra, nell'ordine di arrivo. */
public final class SerialPlayerLock implements PlayerLock {

    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    @Override
    public <T> CompletableFuture<T> withLock(UUID player, Supplier<CompletableFuture<T>> action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture<Void> previous = tails.put(player, done); // atomico: chi arriva dopo aspetta "done"
        CompletableFuture<Void> start = previous == null ? CompletableFuture.completedFuture(null) : previous;
        start.whenComplete((ignored, alsoIgnored) -> {
            CompletableFuture<T> running;
            try {
                running = Objects.requireNonNull(action.get(), "l'azione ha restituito null");
            } catch (Throwable t) {
                running = CompletableFuture.failedFuture(t);
            }
            running.whenComplete((value, error) -> {
                tails.remove(player, done); // solo se nessuno si è accodato dopo
                done.complete(null);
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(value);
                }
            });
        });
        return result;
    }

    int activeKeys() {
        return tails.size();
    }
}
