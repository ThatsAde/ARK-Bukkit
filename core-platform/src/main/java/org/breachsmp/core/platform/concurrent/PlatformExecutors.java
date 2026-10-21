package org.breachsmp.core.platform.concurrent;

import org.breachsmp.core.api.concurrent.CoreExecutors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Executor della spec §3. MAIN è fornito dall'esterno (scheduler Bukkit); DB, COMPUTE e MARKET sono pool propri. */
public final class PlatformExecutors implements CoreExecutors, AutoCloseable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Executor main;
    private final ThreadPoolExecutor db;
    private final ThreadPoolExecutor compute;
    private final ThreadPoolExecutor market;

    public PlatformExecutors(Executor main, int dbThreads, int computeThreads) {
        if (dbThreads < 1 || computeThreads < 1) {
            throw new IllegalArgumentException("Servono almeno 1 thread DB e 1 thread COMPUTE");
        }
        this.main = Objects.requireNonNull(main, "main");
        this.db = fixedPool("breach-db", dbThreads);
        this.compute = fixedPool("breach-compute", computeThreads);
        this.market = fixedPool("breach-market", 1);
    }

    @Override
    public Executor main() {
        return main;
    }

    @Override
    public Executor db() {
        return db;
    }

    @Override
    public Executor compute() {
        return compute;
    }

    @Override
    public Executor market() {
        return market;
    }

    /** Task in coda per executor, nell'ordine db, compute, market (per {@code /breach health}). */
    public Map<String, Integer> queueDepths() {
        Map<String, Integer> depths = new LinkedHashMap<>();
        depths.put("db", db.getQueue().size());
        depths.put("compute", compute.getQueue().size());
        depths.put("market", market.getQueue().size());
        return depths;
    }

    /** Ferma i pool in ordine market → compute → db: il DB per ultimo, così i flush finali possono ancora scrivere. */
    @Override
    public void close() {
        shutdown(market);
        shutdown(compute);
        shutdown(db);
    }

    private static ThreadPoolExecutor fixedPool(String name, int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                Thread.ofPlatform().name(name + "-", 1).daemon(true).factory());
    }

    private static void shutdown(ThreadPoolExecutor executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
