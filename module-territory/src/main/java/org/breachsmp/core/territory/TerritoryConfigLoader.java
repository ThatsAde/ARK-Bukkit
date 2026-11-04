package org.breachsmp.core.territory;

import org.breachsmp.core.domain.territory.RecipeSpec;
import org.breachsmp.core.domain.territory.TerritoryConfig;
import org.breachsmp.core.domain.territory.TerritoryConfigException;
import org.breachsmp.core.domain.territory.TerritoryConfigParser;
import org.breachsmp.core.domain.territory.UpgradeRule;
import org.bukkit.Material;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legge {@code territory.yml} (D-38): SnakeYAML con {@link SafeConstructor} come {@code ConfigLoader} di M0, poi
 * {@link TerritoryConfigParser} e i controlli che servono a Bukkit. Tutti gli errori insieme in una
 * {@link TerritoryConfigException}: avvio fallito (D-08). I controlli sono in due tempi perché {@link Material#isBlock()} e
 * simili leggono il registry del server: {@link #load} (in {@code onLoad}) verifica che ogni materiale esista,
 * {@link #checkBlocks} (in {@code onEnable}, server pronto) che il cupboard sia un blocco e gli upgrade blocchi pieni (D-41).
 */
public final class TerritoryConfigLoader {

    private TerritoryConfigLoader() {
    }

    public static TerritoryConfig load(Path file) {
        Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (IOException | YAMLException e) {
            throw new TerritoryConfigException(List.of("impossibile leggere " + file.getFileName() + ": " + e.getMessage()));
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new TerritoryConfigException(List.of(file.getFileName() + " deve contenere una mappa di chiavi"));
        }
        @SuppressWarnings("unchecked")
        TerritoryConfig config = TerritoryConfigParser.parse((Map<String, Object>) map);
        List<String> errors = new ArrayList<>();
        config.tiers().tieredMaterials().forEach(material -> requireMaterial(material, "tiers", errors));
        for (UpgradeRule rule : config.tiers().upgrades()) {
            requireMaterial(rule.from(), "upgrades." + rule.from(), errors);
            requireMaterial(rule.to(), "upgrades." + rule.from() + ".to", errors);
            rule.cost().keySet().forEach(material -> requireMaterial(material, "upgrades." + rule.from() + ".cost", errors));
        }
        requireMaterial(config.cupboardMaterial(), "cupboard.material", errors);
        requireMaterial(config.hammerMaterial(), "hammer.material", errors);
        checkRecipe(config.cupboardRecipe(), "cupboard.recipe", errors);
        checkRecipe(config.hammerRecipe(), "hammer.recipe", errors);
        if (!errors.isEmpty()) {
            throw new TerritoryConfigException(errors);
        }
        return config;
    }

    /** Il cupboard è un blocco, gli upgrade vanno da un blocco pieno a un blocco pieno (D-41). Richiede il server. */
    public static void checkBlocks(TerritoryConfig config) {
        List<String> errors = new ArrayList<>();
        Material cupboard = requireMaterial(config.cupboardMaterial(), "cupboard.material", errors);
        if (cupboard != null && !cupboard.isBlock()) {
            errors.add("cupboard.material: " + cupboard + " non è un blocco");
        }
        for (UpgradeRule rule : config.tiers().upgrades()) {
            requireSolidBlock(rule.from(), "upgrades." + rule.from(), errors);
            requireSolidBlock(rule.to(), "upgrades." + rule.from() + ".to", errors);
        }
        if (!errors.isEmpty()) {
            throw new TerritoryConfigException(errors);
        }
    }

    private static void checkRecipe(RecipeSpec recipe, String path, List<String> errors) {
        recipe.ingredients().forEach((slot, material) -> requireMaterial(material, path + ".ingredients." + slot, errors));
    }

    private static void requireSolidBlock(String name, String path, List<String> errors) {
        Material material = requireMaterial(name, path, errors);
        if (material != null && !(material.isBlock() && material.isSolid() && material.isOccluding())) {
            errors.add(path + ": " + name + " non è un blocco pieno (D-41)");
        }
    }

    private static Material requireMaterial(String name, String path, List<String> errors) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            errors.add(path + ": materiale sconosciuto " + name);
        }
        return material;
    }
}
