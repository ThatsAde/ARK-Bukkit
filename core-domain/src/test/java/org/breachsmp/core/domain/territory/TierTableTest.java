package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TierTableTest {

    private final TierTable tiers = TerritoryConfigParser.parse(TerritoryConfigParserTest.valid()).tiers();

    @Test
    void namesHpAndMaterials() {
        assertEquals("Legno", tiers.name(1));
        assertEquals(10, tiers.hp(1));
        assertEquals(Set.of("OAK_PLANKS", "OAK_DOOR", "COBBLESTONE"), tiers.tieredMaterials());
        assertThrows(IllegalArgumentException.class, () -> tiers.hp(7));
    }

    @Test
    void upgradeOnlyWhereConfigured() {
        assertEquals(Optional.empty(), tiers.upgradeOf("COBBLESTONE"));
        assertEquals("COBBLESTONE", tiers.upgradeOf("oak_planks").orElseThrow().to());
        assertEquals(List.of(tiers.upgradeOf("OAK_PLANKS").orElseThrow()), List.copyOf(tiers.upgrades()));
    }
}
