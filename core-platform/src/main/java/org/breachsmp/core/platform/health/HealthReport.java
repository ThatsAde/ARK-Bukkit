package org.breachsmp.core.platform.health;

import org.breachsmp.core.api.module.HealthStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Fotografia dello stato del plugin per {@code /breach health}. Le mappe mantengono l'ordine ricevuto. */
public record HealthReport(boolean databaseUp, Map<String, HealthStatus> modules, List<String> details,
                           Map<String, Integer> queueDepths) {

    public HealthReport {
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(modules));
        details = List.copyOf(details);
        queueDepths = Collections.unmodifiableMap(new LinkedHashMap<>(queueDepths));
    }

    /** DB giù o un modulo FAILED → FAILED; un modulo DEGRADED o STARTING → DEGRADED; i DISABLED non contano. */
    public HealthStatus overall() {
        if (!databaseUp || modules.containsValue(HealthStatus.FAILED)) {
            return HealthStatus.FAILED;
        }
        if (modules.containsValue(HealthStatus.DEGRADED) || modules.containsValue(HealthStatus.STARTING)) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.HEALTHY;
    }

    /** Righe da mostrare in chat/console: stato complessivo, database, moduli, dettagli (moduli e piattaforma), code. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("BreachCore: " + overall());
        lines.add("Database: " + (databaseUp ? "OK" : "NON RAGGIUNGIBILE"));
        if (modules.isEmpty()) {
            lines.add("Moduli: nessuno");
        } else {
            modules.forEach((id, status) -> lines.add("Modulo " + id + ": " + status));
        }
        lines.addAll(details);
        lines.add("Code: " + queueDepths.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" ")));
        return lines;
    }
}
