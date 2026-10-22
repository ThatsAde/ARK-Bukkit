package org.breachsmp.core.domain.schedule;

import java.time.LocalDateTime;

/** Calcoli di orario per i job pianificati. */
public final class Schedules {

    private Schedules() {
    }

    /** Prossima occorrenza di {@code hour}:00 strettamente dopo {@code now}. */
    public static LocalDateTime nextDaily(LocalDateTime now, int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Ora fuori da [0, 23]: " + hour);
        }
        LocalDateTime candidate = now.toLocalDate().atTime(hour, 0);
        return candidate.isAfter(now) ? candidate : candidate.plusDays(1);
    }
}
