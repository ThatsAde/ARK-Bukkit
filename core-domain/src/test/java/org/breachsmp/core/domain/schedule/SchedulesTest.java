package org.breachsmp.core.domain.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchedulesTest {

    @Test
    void nextDailyIsStrictlyAfterNow() {
        assertEquals(LocalDateTime.of(2026, 10, 22, 4, 0), Schedules.nextDaily(LocalDateTime.of(2026, 10, 22, 3, 59), 4));
        assertEquals(LocalDateTime.of(2026, 10, 23, 4, 0), Schedules.nextDaily(LocalDateTime.of(2026, 10, 22, 4, 0), 4));
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), Schedules.nextDaily(LocalDateTime.of(2026, 10, 22, 23, 30), 0));
        assertThrows(IllegalArgumentException.class, () -> Schedules.nextDaily(LocalDateTime.now(), 24));
    }
}
