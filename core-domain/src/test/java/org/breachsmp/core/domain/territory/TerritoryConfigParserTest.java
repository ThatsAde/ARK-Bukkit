package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryConfigParserTest {

    /** Stessa forma che SnakeYAML produce da territory.yml (le chiavi numeriche dei tier arrivano come Integer). */
    static Map<String, Object> valid() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("config_version", 1);
        root.put("worlds", List.of("world"));
        root.put("default_tier", 1);
        Map<Object, Object> tiers = new LinkedHashMap<>();
        tiers.put(1, Map.of("name", "Legno", "hp", 10, "materials", List.of("oak_planks", "OAK_DOOR")));
        tiers.put(2, Map.of("name", "Pietra", "hp", 30, "materials", List.of("COBBLESTONE")));
        root.put("tiers", tiers);
        root.put("upgrades", Map.of("OAK_PLANKS", Map.of("to", "COBBLESTONE", "cost", Map.of("COBBLESTONE", 1))));
        root.put("cupboard", Map.of("material", "BARREL", "tier", 2,
                "recipe", Map.of("pattern", List.of("PPP", "PIP", "PPP"), "ingredients", Map.of("P", "OAK_PLANKS", "I", "IRON_INGOT"))));
        root.put("hammer", Map.of("material", "BRUSH",
                "recipe", Map.of("pattern", List.of(" I", "S "), "ingredients", Map.of("I", "IRON_INGOT", "S", "STICK"))));
        return root;
    }

    @Test
    void parsesTiersUpgradesItemsAndRecipes() {
        TerritoryConfig config = TerritoryConfigParser.parse(valid());
        assertEquals(1, config.tiers().tierOf("OAK_DOOR"));
        assertEquals(1, config.tiers().tierOf("oak_planks"));
        assertEquals(2, config.tiers().tierOf("COBBLESTONE"));
        assertEquals(1, config.tiers().tierOf("DIRT"));
        assertEquals(30, config.tiers().hp(2));
        assertEquals(new UpgradeRule("OAK_PLANKS", "COBBLESTONE", Map.of("COBBLESTONE", 1)),
                config.tiers().upgradeOf("OAK_PLANKS").orElseThrow());
        assertEquals("BARREL", config.cupboardMaterial());
        assertEquals(2, config.cupboardTier());
        assertEquals(List.of("PPP", "PIP", "PPP"), config.cupboardRecipe().pattern());
        assertEquals("STICK", config.hammerRecipe().ingredients().get('S'));
        assertEquals(List.of("world"), config.worlds());
    }

    @Test
    void collectsEveryErrorAtOnce() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("config_version", 2);
        root.put("worlds", List.of("world"));
        root.put("default_tier", 9);
        Map<Object, Object> tiers = new LinkedHashMap<>();
        tiers.put(1, Map.of("name", "Legno", "hp", 0, "materials", List.of("OAK_PLANKS")));
        tiers.put(2, Map.of("name", "Pietra", "hp", 30, "materials", List.of("OAK_PLANKS", "COBBLESTONE")));
        root.put("tiers", tiers);
        root.put("upgrades", Map.of("COBBLESTONE", Map.of("to", "OAK_PLANKS", "cost", Map.of("STICK", 0))));
        TerritoryConfigException error = assertThrows(TerritoryConfigException.class, () -> TerritoryConfigParser.parse(root));
        List<String> errors = error.errors();
        assertTrue(errors.contains("config_version deve essere 1, trovato 2"), errors.toString());
        assertTrue(errors.contains("default_tier 9 non è un tier definito"), errors.toString());
        assertTrue(errors.contains("tiers.1.hp deve essere > 0"), errors.toString());
        assertTrue(errors.contains("OAK_PLANKS è in più tier (1, 2)"), errors.toString());
        assertTrue(errors.contains("upgrades.COBBLESTONE: il tier di OAK_PLANKS (1) non è superiore a quello di partenza (2)"),
                errors.toString());
        assertTrue(errors.contains("upgrades.COBBLESTONE.cost.STICK deve essere > 0"), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("cupboard")), "sezione cupboard mancante: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("hammer")), "sezione hammer mancante: " + errors);
    }

    @Test
    void recipeErrorsAreReportedWithTheirSection() {
        Map<String, Object> root = valid();
        root.put("hammer", Map.of("material", "BRUSH",
                "recipe", Map.of("pattern", List.of("XX"), "ingredients", Map.of("I", "IRON_INGOT"))));
        TerritoryConfigException error = assertThrows(TerritoryConfigException.class, () -> TerritoryConfigParser.parse(root));
        assertTrue(error.errors().stream().anyMatch(e -> e.startsWith("hammer.recipe: ")), error.errors().toString());
    }
}
