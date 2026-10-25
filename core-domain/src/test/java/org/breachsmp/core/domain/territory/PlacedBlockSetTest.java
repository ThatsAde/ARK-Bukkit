package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedBlockSetTest {

    @Test
    void addContainsRemoveAndRoundTripThroughTheArray() {
        PlacedBlockSet set = PlacedBlockSet.fromArray(new int[0]);
        assertTrue(set.add(15, -64, 0));
        assertTrue(set.add(0, 319, 15));
        assertFalse(set.add(15, -64, 0));
        PlacedBlockSet copy = PlacedBlockSet.fromArray(set.toArray());
        assertTrue(copy.contains(0, 319, 15));
        assertTrue(copy.remove(15, -64, 0));
        assertFalse(copy.remove(15, -64, 0));
        assertFalse(copy.contains(15, -64, 0));
        assertEquals(1, copy.size());
    }

    @Test
    void rejectsCoordinatesOutsideAChunk() {
        PlacedBlockSet set = PlacedBlockSet.fromArray(new int[0]);
        assertThrows(IllegalArgumentException.class, () -> set.add(16, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> set.add(0, 2048, 0));
        assertThrows(IllegalArgumentException.class, () -> set.add(0, 64, -1));
    }

    @Test
    void arrayFromThePdcIsSortedAndUnsortedInputIsAccepted() {
        int a = PlacedBlockSet.pack(3, 70, 4);
        int b = PlacedBlockSet.pack(1, 60, 2);
        PlacedBlockSet set = PlacedBlockSet.fromArray(new int[]{a, b});
        assertTrue(set.contains(3, 70, 4));
        assertTrue(set.contains(1, 60, 2));
        int[] out = set.toArray();
        assertTrue(out[0] < out[1]);
    }
}
