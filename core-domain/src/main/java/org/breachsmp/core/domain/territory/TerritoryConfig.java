package org.breachsmp.core.domain.territory;

import java.util.List;

/** Contenuto validato di {@code territory.yml} (D-38). I materiali sono nomi Bukkit in maiuscolo. */
public record TerritoryConfig(TierTable tiers, List<String> worlds, String cupboardMaterial, int cupboardTier,
                              RecipeSpec cupboardRecipe, String hammerMaterial, RecipeSpec hammerRecipe) {

    public TerritoryConfig {
        worlds = List.copyOf(worlds);
    }
}
