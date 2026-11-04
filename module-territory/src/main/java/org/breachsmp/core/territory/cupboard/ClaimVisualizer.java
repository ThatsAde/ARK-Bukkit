package org.breachsmp.core.territory.cupboard;

import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.paper.PaperBridge;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Confine di un claim in particelle, solo per chi lo chiede (portato da ToolCupboards {@code ClaimVisualizer}): i 12 spigoli
 * del cubo per 10 secondi. Agli altri giocatori la forma di una base non si mostra.
 */
public final class ClaimVisualizer {

    private static final int FRAMES = 10;
    private static final long FRAME_TICKS = 20L;
    /** Blocchi tra due punti di uno spigolo. */
    private static final double STEP = 2.0D;

    private final PaperBridge bridge;

    public ClaimVisualizer(PaperBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    public void show(Player viewer, ClaimBox box) {
        World world = viewer.getWorld();
        if (!world.getUID().equals(box.world())) {
            return;
        }
        List<Location> outline = outline(world, box);
        int[] frame = {0};
        bridge.plugin().getServer().getScheduler().runTaskTimer(bridge.plugin(), task -> {
            if (frame[0]++ >= FRAMES || !viewer.isOnline() || !viewer.getWorld().getUID().equals(box.world())) {
                task.cancel();
                return;
            }
            for (Location point : outline) {
                viewer.spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }, 0L, FRAME_TICKS);
    }

    /** Il cubo copre i blocchi da {@code centro - raggio} a {@code centro + raggio}: gli spigoli stanno sui bordi esterni. */
    private static List<Location> outline(World world, ClaimBox box) {
        double minX = box.x() - box.radius();
        double maxX = box.x() + box.radius() + 1;
        double minY = box.y() - box.radius();
        double maxY = box.y() + box.radius() + 1;
        double minZ = box.z() - box.radius();
        double maxZ = box.z() + box.radius() + 1;
        double side = 2.0D * box.radius() + 1;
        List<Location> points = new ArrayList<>();
        for (double offset = 0.0D; offset <= side; offset += STEP) {
            double x = minX + offset;
            double y = minY + offset;
            double z = minZ + offset;
            for (double[] pair : new double[][]{{minY, minZ}, {minY, maxZ}, {maxY, minZ}, {maxY, maxZ}}) {
                points.add(new Location(world, x, pair[0], pair[1]));
            }
            for (double[] pair : new double[][]{{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}}) {
                points.add(new Location(world, pair[0], y, pair[1]));
            }
            for (double[] pair : new double[][]{{minX, minY}, {minX, maxY}, {maxX, minY}, {maxX, maxY}}) {
                points.add(new Location(world, pair[0], pair[1], z));
            }
        }
        return points;
    }
}
