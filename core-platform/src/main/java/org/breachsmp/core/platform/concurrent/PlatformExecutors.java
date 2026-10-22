package org.breachsmp.core.platform.concurrent;

import org.breachsmp.core.api.concurrent.CoreExecutors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Executor della spec §3. MAIN è fornito dall'esterno (scheduler Bukkit); DB, COMPUTE e MARKET sono pool propri. */
public final class PlatformExecutors implements CoreExecutors, AutoCloseable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Executor main;
    private final ThreadPoolExecutor db;
    private final ThreadPoolExecutor compute;
    private final ThreadPoolExecutor market;
    private final ScheduledExecutorService timer;

    public PlatformExecutors(Executor main, int dbThreads, int computeThreads) {
        if (dbThreads < 1 || computeThreads < 1) {
            throw new IllegalArgumentException("Servono almeno 1 thread DB e 1 thread COMPUTE");
        }
        this.main = Objects.requireNonNull(main, "main");
        this.db = fixedPool("breach-db", dbThreads);
        this.compute = fixedPool("breach-compute", computeThreads);
        this.market = fixedPool("breach-market", 1);
        this.timer = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("breach-timer").daemon(true).factory());
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

    /** Timer di piattaforma (D-26): solo per passare lavoro agli altri executor a intervalli. Mai I/O né Bukkit qui. */
    public ScheduledExecutorService timer() {
        return timer;
    }

    /** Task in coda per executor, nell'ordine db, compute, market (per {@code /breach health}). */
    public Map<String, Integer> queueDepths() {
        Map<String, Integer> depths = new LinkedHashMap<>();
        depths.put("db", db.getQueue().size());
        depths.put("compute", compute.getQueue().size());
        depths.put("market", market.getQueue().size());
        return depths;
    }

    /**
     * Ferma il timer, poi i pool in ordine market → compute → db, attendendo fino a 10 s ciascuno che la coda si svuoti.
     * Un interrupt del chiamante non salta il drain dei pool successivi: il flag viene ripristinato alla fine.
     */
    @Override
    public void close() {
        timer.shutdownNow(); // il timer passa solo lavoro agli altri pool: non ha nulla da drenare
        boolean interrupted = false;
        for (ThreadPoolExecutor executor : List.of(market, compute, db)) {
            executor.shutdown();
            interrupted |= awaitDrain(executor);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolExecutor fixedPool(String name, int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                Thread.ofPlatform().name(name + "-", 1).daemon(true).factory());
    }

    /** @return {@code true} se il thread chiamante è stato interrotto durante l'attesa */
    private static boolean awaitDrain(ThreadPoolExecutor executor) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_TIMEOUT_SECONDS);
        while (true) {
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    executor.shutdownNow();
                }
                return interrupted;
            } catch (InterruptedException e) {
                interrupted = true; // l'eccezione ha già azzerato il flag: si continua ad attendere
            }
        }
    }
}
