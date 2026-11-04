package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpgradePlannerTest {

    private static final TierTable TIERS = TerritoryConfigParser.parse(config()).tiers();
    private static final UpgradeRule RULE = new UpgradeRule("OAK_PLANKS", "COBBLESTONE", Map.of("COBBLESTONE", 2));

    @Test
    void upgradeWhenEveryMaterialIsThere() {
        assertEquals(new UpgradePlanner.Upgrade(RULE), UpgradePlanner.plan(TIERS, "OAK_PLANKS", Map.of("COBBLESTONE", 3)));
    }

    @Test
    void reportsExactlyWhatIsMissing() {
        // regola COBBLESTONE → POLISHED_DEEPSLATE, costo { COBBLED_DEEPSLATE: 1, IRON_NUGGET: 2 }
        assertEquals(new UpgradePlanner.MissingMaterials(Map.of("IRON_NUGGET", 1)),
                UpgradePlanner.plan(TIERS, "COBBLESTONE", Map.of("COBBLED_DEEPSLATE", 5, "IRON_NUGGET", 1)));
    }

    @Test
    void noUpgradeForTheLastTierOrUnknownBlocks() {
        assertEquals(new UpgradePlanner.NoUpgrade(), UpgradePlanner.plan(TIERS, "NETHERITE_BLOCK", Map.of()));
        assertEquals(new UpgradePlanner.NoUpgrade(), UpgradePlanner.plan(TIERS, "DIRT", Map.of()));
    }

    private static Map<String, Object> config() {
        Map<String, Object> root = TerritoryConfigParserTest.valid();
        Map<Object, Object> tiers = new LinkedHashMap<>();
        tiers.put(1, Map.of("name", "Legno", "hp", 10, "materials", List.of("OAK_PLANKS")));
        tiers.put(2, Map.of("name", "Pietra", "hp", 30, "materials", List.of("COBBLESTONE")));
        tiers.put(3, Map.of("name", "Rinforzato", "hp", 60, "materials", List.of("POLISHED_DEEPSLATE")));
        tiers.put(5, Map.of("name", "Corazzato", "hp", 250, "materials", List.of("NETHERITE_BLOCK")));
        root.put("tiers", tiers);
        root.put("upgrades", Map.of(
                "OAK_PLANKS", Map.of("to", "COBBLESTONE", "cost", Map.of("COBBLESTONE", 2)),
                "COBBLESTONE", Map.of("to", "POLISHED_DEEPSLATE", "cost", Map.of("COBBLED_DEEPSLATE", 1, "IRON_NUGGET", 2))));
        return root;
    }
}
