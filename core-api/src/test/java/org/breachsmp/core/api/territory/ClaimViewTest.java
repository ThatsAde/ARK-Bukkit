package org.breachsmp.core.api.territory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimViewTest {

    private static final UUID W = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    @Test
    void containsIsACubeAroundTheCupboard() {
        ClaimView claim = new ClaimView(1, W, 0, 64, 0, 2);
        assertTrue(claim.contains(W, 2, 66, -2));
        assertFalse(claim.contains(W, 3, 64, 0));
        assertFalse(claim.contains(OTHER, 0, 64, 0));
    }

    @Test
    void rejectsInvalidRadiusAndMissingWorld() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimView(1, W, 0, 64, 0, 0));
        assertThrows(NullPointerException.class, () -> new ClaimView(1, null, 0, 64, 0, 2));
    }
}
