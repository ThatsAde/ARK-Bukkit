package org.breachsmp.core.domain.territory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Decide l'upgrade di un blocco col martello (design M2, T13, D-41): cosa diventa e quali materiali mancano. */
public final class UpgradePlanner {

    public sealed interface Plan permits Upgrade, NoUpgrade, MissingMaterials {
    }

    /** Si può migliorare: l'inventario copre tutto il costo. */
    public record Upgrade(UpgradeRule rule) implements Plan {
    }

    /** Il blocco non ha un tier superiore (ultimo tier o materiale senza upgrade). */
    public record NoUpgrade() implements Plan {
    }

    /** @param missing materiale → quanti ne mancano */
    public record MissingMaterials(Map<String, Integer> missing) implements Plan {
        public MissingMaterials {
            missing = Map.copyOf(missing);
        }
    }

    private UpgradePlanner() {
    }

    /** @param inventory materiale (nome Bukkit in maiuscolo) → quantità posseduta */
    public static Plan plan(TierTable tiers, String blockMaterial, Map<String, Integer> inventory) {
        Objects.requireNonNull(inventory, "inventory");
        Optional<UpgradeRule> rule = tiers.upgradeOf(blockMaterial);
        if (rule.isEmpty()) {
            return new NoUpgrade();
        }
        Map<String, Integer> missing = new LinkedHashMap<>();
        rule.get().cost().forEach((material, needed) -> {
            int owned = inventory.getOrDefault(material, 0);
            if (owned < needed) {
                missing.put(material, needed - owned);
            }
        });
        return missing.isEmpty() ? new Upgrade(rule.get()) : new MissingMaterials(missing);
    }
}
