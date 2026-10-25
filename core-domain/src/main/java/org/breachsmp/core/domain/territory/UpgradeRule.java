package org.breachsmp.core.domain.territory;

import java.util.Map;
import java.util.Objects;

/**
 * Upgrade di un blocco col martello (design M2, T13): da {@code from} a {@code to}, pagando solo materiali (D-41).
 *
 * @param cost materiale → quantità (&gt; 0); i nomi sono quelli Bukkit in maiuscolo
 */
public record UpgradeRule(String from, String to, Map<String, Integer> cost) {

    public UpgradeRule {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        cost = Map.copyOf(cost);
    }
}
