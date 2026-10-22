package org.breachsmp.core.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowLimiterTest {

    @Test
    void allowsUpToLimitPerWindowAndPerKey() {
        TestClock clock = new TestClock(Instant.parse("2026-10-22T10:00:00Z"));
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(clock, Duration.ofMinutes(1));
        UUID alice = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(alice, 2));
        assertTrue(limiter.tryAcquire(alice, 2));
        assertFalse(limiter.tryAcquire(alice, 2));
        assertTrue(limiter.tryAcquire(UUID.randomUUID(), 2));
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.tryAcquire(alice, 2));
        limiter.forget(alice);
        assertTrue(limiter.tryAcquire(alice, 1));
    }

    private static final class TestClock extends Clock {

        private Instant now;

        TestClock(Instant start) {
            now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
