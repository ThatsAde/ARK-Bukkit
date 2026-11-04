package org.breachsmp.core.territory.hammer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.breachsmp.core.domain.territory.ProtectionRules;
import org.breachsmp.core.domain.territory.ProtectionRules.Spot;
import org.breachsmp.core.domain.territory.TierTable;
import org.breachsmp.core.domain.territory.UpgradePlanner;
import org.breachsmp.core.territory.block.PlacedBlockTracker;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.claim.LiveClaim;
import org.breachsmp.core.territory.cupboard.TerritoryItems;
import org.breachsmp.core.territory.protect.ProtectionListener;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Martello da costruzione (design M2, T13, D-41): clic destro su un blocco della propria base lo porta al tier successivo
 * pagando materiali. Contano solo gli item semplici (senza nome, lore o segni): un Tool Cupboard non si spende mai.
 */
public final class HammerListener implements Listener {

    private final ClaimService claims;
    private final PlacedBlockTracker placed;
    private final TerritoryItems items;
    private final TierTable tiers;

    public HammerListener(ClaimService claims, PlacedBlockTracker placed, TerritoryItems items, TierTable tiers) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.placed = Objects.requireNonNull(placed, "placed");
        this.items = Objects.requireNonNull(items, "items");
        this.tiers = Objects.requireNonNull(tiers, "tiers");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || !items.isHammer(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!mayHammer(player, block)) {
            deny(player, "Puoi migliorare solo i blocchi della tua base.");
            return;
        }
        PlayerInventory inventory = player.getInventory();
        switch (UpgradePlanner.plan(tiers, block.getType().name(), plainCounts(inventory))) {
            case UpgradePlanner.Upgrade upgrade -> {
                Material target = Material.matchMaterial(upgrade.rule().to());
                if (target == null) {
                    deny(player, "Questo blocco non ha un tier superiore.");
                    return;
                }
                upgrade.rule().cost().forEach((material, amount) ->
                        inventory.removeItem(new ItemStack(Objects.requireNonNull(Material.matchMaterial(material)), amount)));
                block.setType(target, true);   // il segno di struttura resta sulla posizione
                player.sendMessage(Component.text("Blocco migliorato a " + tiers.name(tiers.tierOf(upgrade.rule().to())) + ".",
                        NamedTextColor.GREEN));
            }
            case UpgradePlanner.MissingMaterials missing -> deny(player, "Servono ancora: " + missing.missing().entrySet().stream()
                    .map(entry -> entry.getValue() + "× " + entry.getKey())
                    .collect(Collectors.joining(", ")) + ".");
            case UpgradePlanner.NoUpgrade ignored -> deny(player, "Questo blocco non ha un tier superiore.");
        }
    }

    /** Il blocco è struttura di un claim in cui il giocatore è autorizzato (o ha il bypass dello staff). */
    private boolean mayHammer(Player player, Block block) {
        Optional<LiveClaim> claim = claims.claimCovering(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (claim.isEmpty() || claim.get().pending() || !placed.isPlaced(block)) {
            return false;
        }
        boolean authorized = player.hasPermission(ProtectionListener.BYPASS_PERMISSION)
                || claim.get().members().contains(player.getUniqueId());
        return ProtectionRules.allows(ProtectionRules.Action.HAMMER, new Spot(claim.get().id(), authorized, true));
    }

    /** Materiale → quantità, solo per gli stack uguali a quelli vanilla: sono gli unici che {@code removeItem} toglie. */
    private static Map<String, Integer> plainCounts(PlayerInventory inventory) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !stack.getType().isAir() && stack.isSimilar(new ItemStack(stack.getType()))) {
                counts.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    private static void deny(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
