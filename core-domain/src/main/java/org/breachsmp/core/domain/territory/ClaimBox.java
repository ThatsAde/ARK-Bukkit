package org.breachsmp.core.domain.territory;

import java.util.Objects;
import java.util.UUID;

/**
 * Area di un claim: cubo di lato {@code 2 * radius + 1} centrato sul blocco del cupboard (design M2, T2).
 *
 * @param world UUID del mondo
 */
public record ClaimBox(UUID world, int x, int y, int z, int radius) {

    public ClaimBox {
        Objects.requireNonNull(world, "world");
        if (radius < 1) {
            throw new IllegalArgumentException("Il raggio di un claim deve essere almeno 1: " + radius);
        }
    }

    public boolean contains(UUID world, int bx, int by, int bz) {
        return this.world.equals(world)
                && Math.abs(bx - x) <= radius && Math.abs(by - y) <= radius && Math.abs(bz - z) <= radius;
    }

    /** {@code true} se i due cubi, allargati di {@code gap} blocchi, hanno almeno un blocco in comune (stesso mondo). */
    public boolean intersects(ClaimBox other, int gap) {
        int reach = radius + other.radius + gap;
        return world.equals(other.world)
                && Math.abs(other.x - x) <= reach && Math.abs(other.y - y) <= reach && Math.abs(other.z - z) <= reach;
    }

    /** Distanza al quadrato dal cupboard: decide quale claim "vince" dove più claim si sovrappongono (T3). */
    public long squaredDistance(int bx, int by, int bz) {
        long dx = bx - x;
        long dy = by - y;
        long dz = bz - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
