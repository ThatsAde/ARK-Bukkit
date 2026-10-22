package org.breachsmp.core.domain.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rate limit a finestra scorrevole per chiave (spec §5, §15). Thread-safe. Lo stato è in memoria: non è un dato di valore. */
public final class SlidingWindowLimiter {

    private final Clock clock;
    private final Duration window;
    private final ConcurrentHashMap<UUID, ArrayDeque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(Clock clock, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
    }

    /** @return {@code true} se l'evento è permesso (e viene contato); il limite si passa a ogni chiamata, così segue i tunable */
    public boolean tryAcquire(UUID key, long limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Il limite deve essere almeno 1: " + limit);
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        boolean[] allowed = new boolean[1];
        events.compute(key, (k, queue) -> {
            ArrayDeque<Instant> times = queue == null ? new ArrayDeque<>() : queue;
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
            if (times.size() < limit) {
                times.addLast(now);
                allowed[0] = true;
            }
            return times;
        });
        return allowed[0];
    }

    public void forget(UUID key) {
        events.remove(key);
    }
}
