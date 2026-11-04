package org.breachsmp.core.domain.territory;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tier dei blocchi (design M2, T10–T13): materiale → tier, tier → nome e HP, materiale → upgrade. Immutabile;
 * la costruisce {@link TerritoryConfigParser}. I materiali sono nomi Bukkit, confrontati in maiuscolo.
 */
public final class TierTable {

    private final int defaultTier;
    private final Map<Integer, String> names;
    private final Map<Integer, Integer> hps;
    private final Map<String, Integer> tierByMaterial;
    private final Map<String, UpgradeRule> upgrades;

    TierTable(int defaultTier, Map<Integer, String> names, Map<Integer, Integer> hps,
              Map<String, Integer> tierByMaterial, Map<String, UpgradeRule> upgrades) {
        this.defaultTier = defaultTier;
        this.names = Map.copyOf(names);
        this.hps = Map.copyOf(hps);
        this.tierByMaterial = Map.copyOf(tierByMaterial);
        this.upgrades = Map.copyOf(upgrades);
    }

    /** Tier configurato per il materiale, altrimenti {@code default_tier} (T10). */
    public int tierOf(String material) {
        return tierByMaterial.getOrDefault(normalize(material), defaultTier);
    }

    /** @throws IllegalArgumentException se il tier non esiste */
    public int hp(int tier) {
        Integer hp = hps.get(tier);
        if (hp == null) {
            throw new IllegalArgumentException("tier " + tier + " non definito");
        }
        return hp;
    }

    /** @throws IllegalArgumentException se il tier non esiste */
    public String name(int tier) {
        String name = names.get(tier);
        if (name == null) {
            throw new IllegalArgumentException("tier " + tier + " non definito");
        }
        return name;
    }

    public Optional<UpgradeRule> upgradeOf(String material) {
        return Optional.ofNullable(upgrades.get(normalize(material)));
    }

    /** Tutti gli upgrade configurati (per validare i materiali all'avvio). */
    public Collection<UpgradeRule> upgrades() {
        return upgrades.values();
    }

    /** Materiali elencati esplicitamente in un tier. */
    public Set<String> tieredMaterials() {
        return tierByMaterial.keySet();
    }

    static String normalize(String material) {
        return material.trim().toUpperCase(Locale.ROOT);
    }
}
