package org.breachsmp.core.territory.protect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.breachsmp.core.domain.territory.ProtectionRules;
import org.breachsmp.core.domain.territory.ProtectionRules.Action;
import org.breachsmp.core.domain.territory.ProtectionRules.Spot;
import org.breachsmp.core.territory.block.PlacedBlockTracker;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.claim.LiveClaim;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Protezione dei claim (design M2, T7, T8, T23): raccoglie il contesto di ogni evento e chiede a {@link ProtectionRules}.
 * Solo letture in memoria (indice dei claim, registro dei blocchi), nessuna query (I2). Il cupboard lo gestisce
 * {@code CupboardListener}.
 */
public final class ProtectionListener implements Listener {

    /** Lo staff con questo permesso conta come autorizzato in ogni claim. */
    public static final String BYPASS_PERMISSION = "breach.territory.bypass";

    /** Id usato per un claim solo riservato (id 0 = nessun claim per {@link ProtectionRules}). */
    private static final long PENDING_CLAIM = -1L;
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST};

    private final ClaimService claims;
    private final PlacedBlockTracker placed;

    public ProtectionListener(ClaimService claims, PlacedBlockTracker placed) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.placed = Objects.requireNonNull(placed, "placed");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        boolean allowed;
        if (event instanceof BlockMultiPlaceEvent multi) {
            allowed = multi.getReplacedBlockStates().stream()
                    .map(BlockState::getBlock)
                    .allMatch(block -> ProtectionRules.allows(Action.PLACE, spot(block, player)));
        } else {
            allowed = ProtectionRules.allows(Action.PLACE, spot(event.getBlockPlaced(), player));
        }
        if (!allowed) {
            event.setCancelled(true);
            deny(player, "Non puoi costruire in questa base.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (!ProtectionRules.allows(Action.PLACE, spot(event.getBlock(), event.getPlayer()))) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Non puoi costruire in questa base.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (claims.claimByCupboard(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()).isPresent()) {
            return;
        }
        if (!ProtectionRules.allows(Action.BREAK, spot(block, event.getPlayer()))) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Questo blocco è protetto: serve un Breach Core.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material type = block.getType();
        Player player = event.getPlayer();
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
                if (!ProtectionRules.allows(Action.OPEN_DOOR, spot(block, player))) {
                    event.setCancelled(true);
                    deny(player, "Questa porta è chiusa.");
                }
            } else if (Tag.BUTTONS.isTagged(type) || type == Material.LEVER) {
                if (!ProtectionRules.allows(Action.USE_REDSTONE, spot(block, player))) {
                    event.setCancelled(true);
                    deny(player, "Questo comando è bloccato.");
                }
            }
        } else if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL && Tag.PRESSURE_PLATES.isTagged(type)) {
            if (!ProtectionRules.allows(Action.USE_REDSTONE, spot(block, player))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!ProtectionRules.externalDamageAllowed(spot(event.getBlock(), null))) {
            event.setCancelled(true);
        }
    }

    /** Il fuoco nasce nell'aria: è struttura se tocca un blocco piazzato. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        if (cause != BlockIgniteEvent.IgniteCause.SPREAD && cause != BlockIgniteEvent.IgniteCause.LAVA) {
            return;
        }
        Block block = event.getBlock();
        long claim = claimId(block);
        if (claim == ProtectionRules.NO_CLAIM) {
            return;
        }
        boolean structure = placed.isPlaced(block);
        for (int i = 0; i < FACES.length && !structure; i++) {
            structure = placed.isPlaced(block.getRelative(FACES[i]));
        }
        if (!ProtectionRules.externalDamageAllowed(new Spot(claim, false, structure))) {
            event.setCancelled(true);
        }
    }

    /** Enderman, wither, ravager e simili; i blocchi che cadono li segue {@link PlacedBlockTracker}. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            return;
        }
        if (!ProtectionRules.externalDamageAllowed(spot(event.getBlock(), null))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!pistonAllowed(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!pistonAllowed(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        long to = claimId(event.getToBlock());
        if (to != ProtectionRules.NO_CLAIM && !ProtectionRules.flowAllowed(claimId(event.getBlock()), to)) {
            event.setCancelled(true);
        }
    }

    /** Partenza e arrivo di ogni blocco mosso devono stare nel claim del pistone (Review Focus 4). */
    private boolean pistonAllowed(Block piston, List<Block> moved, BlockFace direction) {
        long[] movedClaims = new long[moved.size() * 2];
        for (int i = 0; i < moved.size(); i++) {
            Block block = moved.get(i);
            movedClaims[2 * i] = claimId(block);
            movedClaims[2 * i + 1] = claimId(block.getRelative(direction));
        }
        return ProtectionRules.pistonAllowed(claimId(piston), movedClaims);
    }

    private void removeProtected(List<Block> blocks) {
        blocks.removeIf(block -> !ProtectionRules.externalDamageAllowed(spot(block, null)));
    }

    /** Contesto del blocco; {@code player} {@code null} = danno esterno (nessuno è autorizzato). */
    private Spot spot(Block block, Player player) {
        Optional<LiveClaim> claim = claims.claimCovering(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (claim.isEmpty()) {
            return new Spot(ProtectionRules.NO_CLAIM, false, false);
        }
        LiveClaim live = claim.get();
        boolean authorized = player != null
                && (player.hasPermission(BYPASS_PERMISSION) || live.members().contains(player.getUniqueId()));
        return new Spot(idOf(live), authorized, placed.isPlaced(block));
    }

    private long claimId(Block block) {
        return claims.claimCovering(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ())
                .map(ProtectionListener::idOf)
                .orElse(ProtectionRules.NO_CLAIM);
    }

    private static long idOf(LiveClaim claim) {
        return claim.pending() ? PENDING_CLAIM : claim.id();
    }

    private static void deny(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
