package org.breachsmp.core.api.territory;

import java.util.Objects;
import java.util.UUID;

/**
 * Claim visto dagli altri moduli: cupboard al centro, cubo di lato {@code 2 * radius + 1} (design M2, T2).
 *
 * @param world UUID del mondo
 */
public record ClaimView(long id, UUID world, int x, int y, int z, int radius) {

    public ClaimView {
        Objects.requireNonNull(world, "world");
        if (radius < 1) {
            throw new IllegalArgumentException("Il raggio di un claim deve essere almeno 1: " + radius);
        }
    }

    public boolean contains(UUID world, int bx, int by, int bz) {
        return this.world.equals(world)
                && Math.abs(bx - x) <= radius && Math.abs(by - y) <= radius && Math.abs(bz - z) <= radius;
    }
}
