package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimBoxTest {

    static final UUID W = new UUID(0, 1);
    static final UUID OTHER = new UUID(0, 2);

    @Test
    void cubeContainsItsFaceButNotOneBlockOut() {
        ClaimBox box = new ClaimBox(W, 10, 64, 10, 16);
        assertTrue(box.contains(W, 26, 80, -6));
        assertFalse(box.contains(W, 27, 64, 10));
        assertFalse(box.contains(OTHER, 10, 64, 10));
    }

    @Test
    void intersectsHonoursTheGap() {
        ClaimBox a = new ClaimBox(W, 0, 64, 0, 16);
        assertFalse(a.intersects(new ClaimBox(W, 33, 64, 0, 16), 0));
        assertTrue(a.intersects(new ClaimBox(W, 32, 64, 0, 16), 0));
        assertTrue(a.intersects(new ClaimBox(W, 40, 64, 0, 16), 8));
        assertFalse(a.intersects(new ClaimBox(OTHER, 0, 64, 0, 16), 0));
    }

    @Test
    void squaredDistanceFromTheCupboard() {
        assertEquals(9 + 16, new ClaimBox(W, 0, 64, 0, 16).squaredDistance(3, 64, 4));
    }

    @Test
    void rejectsInvalidRadius() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimBox(W, 0, 64, 0, 0));
    }
}
