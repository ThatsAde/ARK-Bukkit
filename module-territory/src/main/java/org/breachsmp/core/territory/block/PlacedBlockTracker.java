package org.breachsmp.core.territory.block;

import org.breachsmp.core.domain.territory.PlacedBlockSet;
import org.breachsmp.core.paper.PaperBridge;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registro dei blocchi piazzati da giocatori (D-39): nel PDC di ogni chunk, chiave {@code breachcore:placed}, riscritto a ogni
 * modifica; in memoria solo i chunk già letti, caricati alla prima domanda (anche quelli caricati prima di {@code onEnable})
 * e tolti allo scaricamento. Un segno su una posizione che oggi è aria vale come non piazzato. Solo main thread.
 */
public final class PlacedBlockTracker implements Listener {

    private final NamespacedKey key;
    private final NamespacedKey fallingKey;
    private final Map<UUID, Map<Long, PlacedBlockSet>> loaded = new HashMap<>();

    public PlacedBlockTracker(PaperBridge bridge) {
        this.key = new NamespacedKey(bridge.plugin(), "placed");
        this.fallingKey = new NamespacedKey(bridge.plugin(), "placed_falling");
    }

    /** {@code true} se il blocco è struttura: piazzato da un giocatore e non aria. */
    public boolean isPlaced(Block block) {
        return !block.getType().isAir() && set(block).contains(block.getX() & 15, block.getY(), block.getZ() & 15);
    }

    public void markPlaced(Block block) {
        PlacedBlockSet set = set(block);
        if (set.add(block.getX() & 15, block.getY(), block.getZ() & 15)) {
            save(block.getChunk(), set);
        }
    }

    public void unmark(Block block) {
        PlacedBlockSet set = set(block);
        if (set.remove(block.getX() & 15, block.getY(), block.getZ() & 15)) {
            save(block.getChunk(), set);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        // BlockMultiPlaceEvent (letti, porte) condivide la HandlerList di BlockPlaceEvent: arriva qui
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                markPlaced(state.getBlock());
            }
        } else {
            markPlaced(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::unmark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::unmark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    /** Un blocco piazzato che cade resta struttura dove atterra; la sabbia naturale no (segno sull'entità). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        Block block = event.getBlock();
        if (event.getTo().isAir()) {
            if (isPlaced(block)) {
                falling.getPersistentDataContainer().set(fallingKey, PersistentDataType.BOOLEAN, true);
                unmark(block);
            }
        } else if (falling.getPersistentDataContainer().has(fallingKey)) {
            markPlaced(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Map<Long, PlacedBlockSet> chunks = loaded.get(event.getWorld().getUID());
        if (chunks != null) {
            chunks.remove(event.getChunk().getChunkKey());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        loaded.remove(event.getWorld().getUID());
    }

    /** Il pistone sposta i segni prima che i blocchi si muovano: prima si tolgono tutti, poi si rimettono un passo avanti. */
    private void move(List<Block> blocks, BlockFace direction) {
        List<Block> marked = new ArrayList<>();
        for (Block block : blocks) {
            if (isPlaced(block)) {
                marked.add(block);
            }
        }
        marked.forEach(this::unmark);
        for (Block block : marked) {
            markPlaced(block.getRelative(direction));
        }
    }

    private PlacedBlockSet set(Block block) {
        Map<Long, PlacedBlockSet> chunks = loaded.computeIfAbsent(block.getWorld().getUID(), ignored -> new HashMap<>());
        long chunkKey = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
        PlacedBlockSet set = chunks.get(chunkKey);
        if (set == null) {
            int[] stored = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
            set = PlacedBlockSet.fromArray(stored == null ? new int[0] : stored);
            chunks.put(chunkKey, set);
        }
        return set;
    }

    private void save(Chunk chunk, PlacedBlockSet set) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (set.size() == 0) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, set.toArray());
        }
    }
}
