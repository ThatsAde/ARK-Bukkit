package org.breachsmp.core.platform.health;

import org.breachsmp.core.api.module.HealthStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthReportTest {

    private static final Map<String, Integer> QUEUES = queues();

    @Test
    void healthyWhenDatabaseUpAndModulesHealthyOrDisabled() {
        var report = new HealthReport(true, Map.of("economy", HealthStatus.HEALTHY, "arena", HealthStatus.DISABLED), List.of(), QUEUES);
        assertEquals(HealthStatus.HEALTHY, report.overall());
    }

    @Test
    void failedWhenDatabaseDown() {
        assertEquals(HealthStatus.FAILED, new HealthReport(false, Map.of(), List.of(), QUEUES).overall());
    }

    @Test
    void failedWhenAModuleFailed() {
        var report = new HealthReport(true, Map.of("economy", HealthStatus.FAILED), List.of(), QUEUES);
        assertEquals(HealthStatus.FAILED, report.overall());
    }

    @Test
    void degradedWhenAModuleIsDegradedOrStarting() {
        assertEquals(HealthStatus.DEGRADED,
                new HealthReport(true, Map.of("economy", HealthStatus.DEGRADED), List.of(), QUEUES).overall());
        assertEquals(HealthStatus.DEGRADED,
                new HealthReport(true, Map.of("economy", HealthStatus.STARTING), List.of(), QUEUES).overall());
    }

    @Test
    void linesListEverything() {
        Map<String, HealthStatus> modules = new LinkedHashMap<>();
        modules.put("economy", HealthStatus.HEALTHY);
        modules.put("arena", HealthStatus.DISABLED);
        var report = new HealthReport(true, modules, List.of("Massa monetaria: 0 monete"), QUEUES);
        assertEquals(List.of(
                "BreachCore: HEALTHY",
                "Database: OK",
                "Modulo economy: HEALTHY",
                "Modulo arena: DISABLED",
                "Massa monetaria: 0 monete",
                "Code: db=0 compute=1 market=0"), report.lines());
    }

    @Test
    void linesWithoutModulesAndDatabaseDown() {
        assertEquals(List.of(
                "BreachCore: FAILED",
                "Database: NON RAGGIUNGIBILE",
                "Moduli: nessuno",
                "Code: db=0 compute=1 market=0"), new HealthReport(false, Map.of(), List.of(), QUEUES).lines());
    }

    private static Map<String, Integer> queues() {
        Map<String, Integer> queues = new LinkedHashMap<>();
        queues.put("db", 0);
        queues.put("compute", 1);
        queues.put("market", 0);
        return queues;
    }
}
