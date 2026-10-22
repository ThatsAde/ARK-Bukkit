package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.Tone;
import org.breachsmp.core.api.module.HealthStatus;
import org.breachsmp.core.platform.health.HealthReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthSubcommandTest {

    @Test
    void firstLineToneFollowsOverallStatus() {
        CommandResult healthy = HealthSubcommand.toResult(
                new HealthReport(true, Map.of(), List.of("Ultimo job: nessuno"), Map.of("db", 0)));
        assertEquals(Tone.SUCCESS, healthy.lines().getFirst().tone());
        assertEquals(Tone.MUTED, healthy.lines().get(1).tone());
        CommandResult failed = HealthSubcommand.toResult(new HealthReport(false, Map.of(), List.of(), Map.of()));
        assertEquals(Tone.ERROR, failed.lines().getFirst().tone());
        CommandResult degraded = HealthSubcommand.toResult(
                new HealthReport(true, Map.of("economy", HealthStatus.DEGRADED), List.of(), Map.of()));
        assertEquals(Tone.WARNING, degraded.lines().getFirst().tone());
    }
}
