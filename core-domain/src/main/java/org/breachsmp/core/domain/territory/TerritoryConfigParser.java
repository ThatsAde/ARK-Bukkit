package org.breachsmp.core.domain.territory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Valida {@code territory.yml} già caricato come mappe annidate (forma di SnakeYAML) e costruisce
 * {@link TerritoryConfig}. Raccoglie TUTTI gli errori e li lancia insieme (fail-closed, D-08/D-38). Non verifica che
 * i materiali esistano in Bukkit: lo fa il modulo.
 */
public final class TerritoryConfigParser {

    public static final int CURRENT_VERSION = 1;

    private TerritoryConfigParser() {
    }

    /** @throws TerritoryConfigException con l'elenco completo degli errori */
    public static TerritoryConfig parse(Map<String, Object> root) {
        List<String> errors = new ArrayList<>();

        Integer version = readInt(root.get("config_version"), "config_version", errors);
        if (version != null && version != CURRENT_VERSION) {
            errors.add("config_version deve essere " + CURRENT_VERSION + ", trovato " + version);
        }

        List<String> worlds = readStringList(root.get("worlds"), "worlds", errors);
        if (worlds != null && worlds.isEmpty()) {
            errors.add("worlds deve elencare almeno un mondo");
        }

        // tiers: chiave numerica → { name, hp, materials }
        Map<Integer, String> names = new TreeMap<>();
        Map<Integer, Integer> hps = new TreeMap<>();
        Map<Integer, List<String>> materialsByTier = new TreeMap<>();
        Map<?, ?> tiersSection = readMap(root.get("tiers"), "tiers", errors);
        if (tiersSection != null) {
            if (tiersSection.isEmpty()) {
                errors.add("tiers deve definire almeno un tier");
            }
            for (Map.Entry<?, ?> entry : tiersSection.entrySet()) {
                String key = String.valueOf(entry.getKey());
                int tier;
                try {
                    tier = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    errors.add("tiers." + key + ": la chiave deve essere un numero intero");
                    continue;
                }
                String path = "tiers." + tier;
                Map<?, ?> body = readMap(entry.getValue(), path, errors);
                if (body == null) {
                    continue;
                }
                String name = readString(body.get("name"), path + ".name", errors);
                Integer hp = readInt(body.get("hp"), path + ".hp", errors);
                if (hp != null && hp <= 0) {
                    errors.add(path + ".hp deve essere > 0");
                }
                List<String> materials = readStringList(body.get("materials"), path + ".materials", errors);
                names.put(tier, name == null ? "" : name);
                hps.put(tier, hp == null ? 0 : hp);
                materialsByTier.put(tier, materials == null ? List.of() : materials);
            }
        }

        // materiale → tier; il primo tier (il più basso) vince, il duplicato è un errore
        Map<String, Integer> tierByMaterial = new LinkedHashMap<>();
        Map<String, List<Integer>> duplicates = new LinkedHashMap<>();
        materialsByTier.forEach((tier, materials) -> {
            for (String raw : materials) {
                String material = TierTable.normalize(raw);
                Integer previous = tierByMaterial.putIfAbsent(material, tier);
                if (previous != null && previous.intValue() != tier) {
                    duplicates.computeIfAbsent(material, m -> new ArrayList<>(List.of(previous))).add(tier);
                }
            }
        });
        duplicates.forEach((material, tiers) -> errors.add(material + " è in più tier ("
                + String.join(", ", tiers.stream().map(String::valueOf).toList()) + ")"));

        Integer defaultTier = readInt(root.get("default_tier"), "default_tier", errors);
        if (defaultTier != null && !hps.containsKey(defaultTier)) {
            errors.add("default_tier " + defaultTier + " non è un tier definito");
        }
        int fallbackTier = defaultTier == null ? 0 : defaultTier;

        // upgrades: materiale → { to, cost: { materiale: quantità } }
        Map<String, UpgradeRule> upgrades = new HashMap<>();
        Object upgradesRaw = root.get("upgrades");
        Map<?, ?> upgradesSection = upgradesRaw == null ? Map.of() : readMap(upgradesRaw, "upgrades", errors);
        if (upgradesSection != null) {
            for (Map.Entry<?, ?> entry : upgradesSection.entrySet()) {
                String from = TierTable.normalize(String.valueOf(entry.getKey()));
                String path = "upgrades." + from;
                Map<?, ?> body = readMap(entry.getValue(), path, errors);
                if (body == null) {
                    continue;
                }
                String toRaw = readString(body.get("to"), path + ".to", errors);
                Map<String, Integer> cost = readCost(body.get("cost"), path + ".cost", errors);
                if (toRaw == null) {
                    continue;
                }
                String to = TierTable.normalize(toRaw);
                int fromTier = tierByMaterial.getOrDefault(from, fallbackTier);
                int toTier = tierByMaterial.getOrDefault(to, fallbackTier);
                if (toTier <= fromTier) {
                    errors.add(path + ": il tier di " + to + " (" + toTier
                            + ") non è superiore a quello di partenza (" + fromTier + ")");
                }
                if (cost != null) {
                    upgrades.put(from, new UpgradeRule(from, to, cost));
                }
            }
        }

        // cupboard e hammer
        String cupboardMaterial = null;
        Integer cupboardTier = null;
        RecipeSpec cupboardRecipe = null;
        Map<?, ?> cupboard = readMap(root.get("cupboard"), "cupboard", errors);
        if (cupboard != null) {
            cupboardMaterial = readMaterial(cupboard.get("material"), "cupboard.material", errors);
            cupboardTier = readInt(cupboard.get("tier"), "cupboard.tier", errors);
            if (cupboardTier != null && !hps.isEmpty() && !hps.containsKey(cupboardTier)) {
                errors.add("cupboard.tier " + cupboardTier + " non è un tier definito");
            }
            cupboardRecipe = readRecipe(cupboard.get("recipe"), "cupboard.recipe", errors);
        }
        String hammerMaterial = null;
        RecipeSpec hammerRecipe = null;
        Map<?, ?> hammer = readMap(root.get("hammer"), "hammer", errors);
        if (hammer != null) {
            hammerMaterial = readMaterial(hammer.get("material"), "hammer.material", errors);
            hammerRecipe = readRecipe(hammer.get("recipe"), "hammer.recipe", errors);
        }

        if (!errors.isEmpty()) {
            throw new TerritoryConfigException(errors);
        }
        TierTable tiers = new TierTable(fallbackTier, names, hps, tierByMaterial, upgrades);
        return new TerritoryConfig(tiers, worlds, cupboardMaterial, cupboardTier, cupboardRecipe,
                hammerMaterial, hammerRecipe);
    }

    private static RecipeSpec readRecipe(Object raw, String path, List<String> errors) {
        Map<?, ?> body = readMap(raw, path, errors);
        if (body == null) {
            return null;
        }
        List<String> pattern = readStringList(body.get("pattern"), path + ".pattern", errors);
        Map<?, ?> ingredientsRaw = readMap(body.get("ingredients"), path + ".ingredients", errors);
        if (pattern == null || ingredientsRaw == null) {
            return null;
        }
        Map<Character, String> ingredients = new HashMap<>();
        boolean valid = true;
        for (Map.Entry<?, ?> entry : ingredientsRaw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.length() != 1 || key.equals(" ")) {
                errors.add(path + ".ingredients: la chiave '" + key + "' deve essere un solo carattere diverso dallo spazio");
                valid = false;
                continue;
            }
            String material = readMaterial(entry.getValue(), path + ".ingredients." + key, errors);
            if (material == null) {
                valid = false;
                continue;
            }
            ingredients.put(key.charAt(0), material);
        }
        if (!valid) {
            return null;
        }
        try {
            return new RecipeSpec(pattern, ingredients);
        } catch (IllegalArgumentException e) {
            errors.add(path + ": " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Integer> readCost(Object raw, String path, List<String> errors) {
        Map<?, ?> body = readMap(raw, path, errors);
        if (body == null) {
            return null;
        }
        if (body.isEmpty()) {
            errors.add(path + " deve elencare almeno un materiale");
            return null;
        }
        Map<String, Integer> cost = new LinkedHashMap<>();
        boolean valid = true;
        for (Map.Entry<?, ?> entry : body.entrySet()) {
            String material = TierTable.normalize(String.valueOf(entry.getKey()));
            Integer amount = readInt(entry.getValue(), path + "." + material, errors);
            if (amount == null) {
                valid = false;
            } else if (amount <= 0) {
                errors.add(path + "." + material + " deve essere > 0");
                valid = false;
            } else {
                cost.put(material, amount);
            }
        }
        return valid ? cost : null;
    }

    private static Integer readInt(Object raw, String path, List<String> errors) {
        if (raw == null) {
            errors.add(path + " mancante");
            return null;
        }
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            long value = ((Number) raw).longValue();
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        errors.add(path + " deve essere un numero intero, trovato " + raw);
        return null;
    }

    private static String readString(Object raw, String path, List<String> errors) {
        if (raw == null) {
            errors.add(path + " mancante");
            return null;
        }
        if (!(raw instanceof String text) || text.isBlank()) {
            errors.add(path + " deve essere un testo non vuoto");
            return null;
        }
        return text;
    }

    private static String readMaterial(Object raw, String path, List<String> errors) {
        String text = readString(raw, path, errors);
        return text == null ? null : TierTable.normalize(text);
    }

    private static List<String> readStringList(Object raw, String path, List<String> errors) {
        if (raw == null) {
            errors.add(path + " mancante");
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            errors.add(path + " deve essere una lista");
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                errors.add(path + " deve contenere solo testi, trovato " + item);
                return null;
            }
            values.add(text);
        }
        return values;
    }

    private static Map<?, ?> readMap(Object raw, String path, List<String> errors) {
        if (raw == null) {
            errors.add(path + " mancante");
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            errors.add(path + " deve essere una sezione");
            return null;
        }
        return map;
    }
}
