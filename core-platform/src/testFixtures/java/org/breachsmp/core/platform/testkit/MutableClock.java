package org.breachsmp.core.platform.testkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Orologio controllabile per i test (zona UTC): {@code LocalDateTime.now(clock)} restituisce l'ora impostata. */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(LocalDateTime start) {
        set(start);
    }

    public void set(LocalDateTime time) {
        now = time.toInstant(ZoneOffset.UTC);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock lavora solo in UTC");
    }

    @Override
    public Instant instant() {
        return now;
    }
}
