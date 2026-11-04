package org.breachsmp.core.territory.cupboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.domain.territory.TerritoryConfig;
import org.breachsmp.core.territory.TerritoryTunables;
import org.breachsmp.core.territory.block.PlacedBlockTracker;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.claim.LiveClaim;
import org.breachsmp.core.territory.claim.PlacementRefusal;
import org.breachsmp.core.territory.protect.ProtectionListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Ciclo di vita del Tool Cupboard (design M2, T3–T6, T18): piazzamento (apre il claim), rottura da un autorizzato (lo chiude
 * e restituisce l'item), interazione (menu per gli autorizzati, PIN per gli altri). Il barile non si apre mai come
 * contenitore (D-42).
 */
public final class CupboardListener implements Listener {

    private final ClaimService claims;
    private final TerritoryItems items;
    private final PlacedBlockTracker placed;
    private final PinDialogs pins;
    private final CupboardMenu menu;
    private final TerritoryTunables tunables;
    private final TerritoryConfig config;
    private final CoreExecutors executors;
    private final Logger logger;

    public CupboardListener(ClaimService claims, TerritoryItems items, PlacedBlockTracker placed, PinDialogs pins,
                            CupboardMenu menu, TerritoryTunables tunables, TerritoryConfig config, CoreExecutors executors,
                            Logger logger) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.items = Objects.requireNonNull(items, "items");
        this.placed = Objects.requireNonNull(placed, "placed");
        this.pins = Objects.requireNonNull(pins, "pins");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.tunables = Objects.requireNonNull(tunables, "tunables");
        this.config = Objects.requireNonNull(config, "config");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!items.isCupboard(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        ClaimBox box = new ClaimBox(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                (int) tunables.claimRadius().get());
        Optional<PlacementRefusal> refusal = claims.refusePlacement(player.getUniqueId(), box,
                (int) tunables.claimGap().get(), tunables.maxCupboardsPerPlayer().get(), worlds());
        if (refusal.isPresent()) {
            event.setCancelled(true);
            deny(player, switch (refusal.get()) {
                case WORLD_DISABLED -> "Questo mondo non ammette basi.";
                case OVERLAPS_FOREIGN_CLAIM -> "Ti sovrapporresti a una base dove non sei autorizzato.";
                case LIMIT_REACHED -> "Hai già il numero massimo di Tool Cupboard.";
                case POSITION_TAKEN -> "Qui c'è già un Tool Cupboard.";
            });
            return;
        }
        claims.create(player.getUniqueId(), box).whenCompleteAsync((claim, error) -> {
            if (error == null) {
                ok(player, "Base protetta. Imposta il PIN.");
                if (player.isOnline()) {
                    pins.askNewPin(player, claim);
                }
                return;
            }
            // Review Focus 2: nessun claim fantasma, il cupboard torna al giocatore
            logger.log(Level.WARNING, "Creazione del claim fallita in " + box, error);
            if (block.getType() == items.cupboardMaterial()) {
                block.setType(Material.AIR);
            }
            placed.unmark(block);
            giveBack(player, block.getLocation());
            deny(player, "Impossibile creare la base, riprova.");
        }, executors.main());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<LiveClaim> found = cupboardAt(block);
        if (found.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        LiveClaim claim = found.get();
        Player player = event.getPlayer();
        if (claim.pending()) {
            deny(player, "La base si sta ancora creando.");
            return;
        }
        if (!authorized(player, claim)) {
            deny(player, "Questo Tool Cupboard è protetto: serve un Breach Core.");
            return;
        }
        claims.remove(claim.id(), player.getUniqueId(), "cupboard rotto").whenCompleteAsync((ignored, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Rimozione del claim #" + claim.id() + " fallita", error);
                deny(player, "Impossibile rimuovere la base, riprova.");
                return;
            }
            if (block.getType() == items.cupboardMaterial()) {
                block.setType(Material.AIR);
            }
            placed.unmark(block);
            block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), items.cupboard(1));
            ok(player, "Base rimossa.");
        }, executors.main());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Optional<LiveClaim> found = cupboardAt(block);
        if (found.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        LiveClaim claim = found.get();
        Player player = event.getPlayer();
        if (claim.pending()) {
            deny(player, "La base si sta ancora creando.");
        } else if (authorized(player, claim)) {
            menu.open(player, claim);
        } else {
            pins.askPin(player, claim);
        }
    }

    /** Le tramogge non riempiono il barile del cupboard. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() != InventoryType.BARREL) {
            return;
        }
        InventoryHolder holder = event.getDestination().getHolder(false);
        if (holder instanceof Barrel barrel && cupboardAt(barrel.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    private Optional<LiveClaim> cupboardAt(Block block) {
        return claims.claimByCupboard(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static boolean authorized(Player player, LiveClaim claim) {
        return player.hasPermission(ProtectionListener.BYPASS_PERMISSION) || claim.members().contains(player.getUniqueId());
    }

    private Set<UUID> worlds() {
        return config.worlds().stream()
                .map(Bukkit::getWorld)
                .filter(Objects::nonNull)
                .map(World::getUID)
                .collect(Collectors.toSet());
    }

    private void giveBack(Player player, Location where) {
        ItemStack cupboard = items.cupboard(1);
        if (player.isOnline()) {
            player.getInventory().addItem(cupboard).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        } else {
            where.getWorld().dropItemNaturally(where.toCenterLocation(), cupboard);
        }
    }

    private static void ok(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static void deny(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
