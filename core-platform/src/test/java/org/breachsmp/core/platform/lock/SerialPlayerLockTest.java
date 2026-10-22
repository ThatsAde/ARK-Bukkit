package org.breachsmp.core.platform.lock;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerialPlayerLockTest {

    private final SerialPlayerLock lock = new SerialPlayerLock();

    @Test
    void actionsOfTheSamePlayerRunOneAtATime() throws Exception {
        UUID alice = UUID.randomUUID();
        CompletableFuture<String> firstWork = new CompletableFuture<>();
        AtomicBoolean secondStarted = new AtomicBoolean();
        CompletableFuture<String> first = lock.withLock(alice, () -> firstWork);
        CompletableFuture<String> second = lock.withLock(alice, () -> {
            secondStarted.set(true);
            return CompletableFuture.completedFuture("due");
        });
        assertFalse(secondStarted.get(), "la seconda azione deve aspettare la prima");
        firstWork.complete("uno");
        assertEquals("uno", first.get(5, SECONDS));
        assertEquals("due", second.get(5, SECONDS));
        assertEquals(0, lock.activeKeys());
    }

    @Test
    void differentPlayersDoNotWaitForEachOther() throws Exception {
        CompletableFuture<String> blocked = new CompletableFuture<>();
        lock.withLock(UUID.randomUUID(), () -> blocked);
        assertEquals("libero", lock.withLock(UUID.randomUUID(), () -> CompletableFuture.completedFuture("libero")).get(5, SECONDS));
    }

    @Test
    void aFailingActionReleasesTheLock() throws Exception {
        UUID alice = UUID.randomUUID();
        CompletableFuture<Object> failed = lock.withLock(alice, () -> {
            throw new IllegalStateException("boom");
        });
        ExecutionException error = assertThrows(ExecutionException.class, () -> failed.get(5, SECONDS));
        assertEquals("boom", error.getCause().getMessage());
        assertEquals("dopo", lock.withLock(alice, () -> CompletableFuture.completedFuture("dopo")).get(5, SECONDS));
    }
}
