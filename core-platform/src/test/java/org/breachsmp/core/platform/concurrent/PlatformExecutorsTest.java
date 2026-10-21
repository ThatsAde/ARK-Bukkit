package org.breachsmp.core.platform.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformExecutorsTest {

    private PlatformExecutors executors;

    @AfterEach
    void tearDown() {
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void threadsAreNamedPerExecutor() throws Exception {
        executors = new PlatformExecutors(Runnable::run, 2, 1);
        assertTrue(threadName(executors.db()).startsWith("breach-db-"));
        assertTrue(threadName(executors.compute()).startsWith("breach-compute-"));
        assertEquals("breach-market-1", threadName(executors.market()));
    }

    @Test
    void marketIsSingleWriter() throws Exception {
        executors = new PlatformExecutors(Runnable::run, 2, 2);
        Set<String> names = ConcurrentHashMap.newKeySet();
        CompletableFuture<?>[] tasks = IntStream.range(0, 20)
                .mapToObj(i -> CompletableFuture.runAsync(() -> names.add(Thread.currentThread().getName()), executors.market()))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).get(5, SECONDS);
        assertEquals(Set.of("breach-market-1"), names);
    }

    @Test
    void mainIsTheGivenExecutor() {
        Executor main = Runnable::run;
        executors = new PlatformExecutors(main, 1, 1);
        assertSame(main, executors.main());
    }

    @Test
    void queueDepthsListsAllExecutorsInOrder() {
        executors = new PlatformExecutors(Runnable::run, 1, 1);
        assertEquals(List.of("db", "compute", "market"), List.copyOf(executors.queueDepths().keySet()));
    }

    @Test
    void closeStopsAcceptingTasks() {
        executors = new PlatformExecutors(Runnable::run, 1, 1);
        executors.close();
        assertThrows(RejectedExecutionException.class, () -> executors.db().execute(() -> { }));
        executors = null;
    }

    @Test
    void rejectsZeroThreads() {
        assertThrows(IllegalArgumentException.class, () -> new PlatformExecutors(Runnable::run, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PlatformExecutors(Runnable::run, 1, 0));
    }

    private static String threadName(Executor executor) throws Exception {
        return CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), executor).get(5, SECONDS);
    }
}
