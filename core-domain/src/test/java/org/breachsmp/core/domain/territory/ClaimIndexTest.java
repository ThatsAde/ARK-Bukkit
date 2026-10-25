package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.breachsmp.core.domain.territory.ClaimBoxTest.W;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimIndexTest {

    @Test
    void coveringReturnsNearestFirstAcrossChunkBorders() {
        ClaimIndex<String> index = new ClaimIndex<>();
        index.add("a", new ClaimBox(W, 0, 64, 0, 16));
        index.add("b", new ClaimBox(W, 20, 64, 0, 16));
        assertEquals(List.of("b", "a"), index.covering(W, 15, 64, 0));
        assertEquals(List.of("a"), index.covering(W, -16, 64, -16));
        assertEquals(List.of(), index.covering(W, 100, 64, 0));
        index.remove("b");
        assertEquals(List.of("a"), index.covering(W, 15, 64, 0));
        assertEquals(1, index.size());
    }

    @Test
    void overlappingFindsNeighboursWithinTheGap() {
        ClaimIndex<String> index = new ClaimIndex<>();
        index.add("a", new ClaimBox(W, 0, 64, 0, 16));
        assertEquals(List.of("a"), index.overlapping(new ClaimBox(W, 30, 64, 0, 16), 0));
        assertEquals(List.of(), index.overlapping(new ClaimBox(W, 40, 64, 0, 16), 0));
        assertEquals(List.of("a"), index.overlapping(new ClaimBox(W, 40, 64, 0, 16), 8));
    }
}
