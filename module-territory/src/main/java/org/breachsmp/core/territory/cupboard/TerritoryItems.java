package org.breachsmp.core.territory.cupboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.breachsmp.core.domain.territory.TerritoryConfig;
import org.breachsmp.core.paper.PaperBridge;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

/**
 * Item del territorio, riconosciuti da un segno nel PDC (portato da ToolCupboards {@code CupboardItems}): chiave
 * {@code breachcore:item} = {@code tool_cupboard} | {@code hammer}. Senza il segno un barile è solo un barile.
 */
public final class TerritoryItems {

    private static final String CUPBOARD = "tool_cupboard";
    private static final String HAMMER = "hammer";

    private final NamespacedKey key;
    private final Material cupboardMaterial;
    private final Material hammerMaterial;

    public TerritoryItems(PaperBridge bridge, TerritoryConfig config) {
        this.key = new NamespacedKey(bridge.plugin(), "item");
        this.cupboardMaterial = material(config.cupboardMaterial());
        this.hammerMaterial = material(config.hammerMaterial());
    }

    public ItemStack cupboard(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Quantità non valida: " + amount);
        }
        return marked(new ItemStack(cupboardMaterial, amount), CUPBOARD, "Tool Cupboard",
                "Piazzalo per proteggere la tua base.");
    }

    public ItemStack hammer() {
        return marked(new ItemStack(hammerMaterial), HAMMER, "Martello da costruzione",
                "Clic destro su un blocco della tua base per migliorarlo.");
    }

    public boolean isCupboard(ItemStack stack) {
        return CUPBOARD.equals(markOf(stack));
    }

    public boolean isHammer(ItemStack stack) {
        return HAMMER.equals(markOf(stack));
    }

    /** Materiale del blocco del cupboard ({@code BARREL} di default, D-42). */
    public Material cupboardMaterial() {
        return cupboardMaterial;
    }

    private ItemStack marked(ItemStack stack, String mark, String name, String lore) {
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, mark);
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        });
        return stack;
    }

    private String markOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static Material material(String name) {
        return Objects.requireNonNull(Material.matchMaterial(name), () -> "Materiale sconosciuto: " + name);
    }
}
