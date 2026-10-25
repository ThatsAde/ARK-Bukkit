package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionRulesTest {

    private static ProtectionRules.Spot spot(long claim, boolean authorized, boolean structure) {
        return new ProtectionRules.Spot(claim, authorized, structure);
    }

    @Test
    void outsideAClaimEverythingIsVanilla() {
        for (ProtectionRules.Action action : ProtectionRules.Action.values()) {
            assertTrue(ProtectionRules.allows(action, spot(0, false, true)), action.name());
        }
        assertTrue(ProtectionRules.externalDamageAllowed(spot(0, false, true)));
    }

    @Test
    void strangersInAClaimCannotBuildBreakStructureOpenDoorsOrUseRedstone() {
        assertFalse(ProtectionRules.allows(ProtectionRules.Action.PLACE, spot(7, false, false)));
        assertFalse(ProtectionRules.allows(ProtectionRules.Action.BREAK, spot(7, false, true)));
        assertFalse(ProtectionRules.allows(ProtectionRules.Action.OPEN_DOOR, spot(7, false, true)));
        assertFalse(ProtectionRules.allows(ProtectionRules.Action.USE_REDSTONE, spot(7, false, true)));
        assertFalse(ProtectionRules.allows(ProtectionRules.Action.HAMMER, spot(7, false, true)));
    }

    @Test
    void strangersCanDigTerrainAndOpenContainers() {
        assertTrue(ProtectionRules.allows(ProtectionRules.Action.BREAK, spot(7, false, false)));
        assertTrue(ProtectionRules.allows(ProtectionRules.Action.OPEN_CONTAINER, spot(7, false, true)));
    }

    @Test
    void authorizedPlayersDoEverything() {
        for (ProtectionRules.Action action : ProtectionRules.Action.values()) {
            assertTrue(ProtectionRules.allows(action, spot(7, true, true)), action.name());
        }
    }

    @Test
    void externalDamageSparesStructureButNotTerrain() {
        assertFalse(ProtectionRules.externalDamageAllowed(spot(7, false, true)));
        assertTrue(ProtectionRules.externalDamageAllowed(spot(7, false, false)));
    }

    @Test
    void pistonMayNotCrossAClaimBoundary() {
        assertTrue(ProtectionRules.pistonAllowed(7, new long[]{7, 7}));
        assertTrue(ProtectionRules.pistonAllowed(0, new long[]{0, 0}));
        assertFalse(ProtectionRules.pistonAllowed(0, new long[]{0, 7}));
        assertFalse(ProtectionRules.pistonAllowed(7, new long[]{7, 0}));
        assertFalse(ProtectionRules.pistonAllowed(7, new long[]{8}));
    }

    @Test
    void fluidsMayNotFlowIntoAnotherClaim() {
        assertTrue(ProtectionRules.flowAllowed(7, 7));
        assertTrue(ProtectionRules.flowAllowed(7, 0));
        assertFalse(ProtectionRules.flowAllowed(0, 7));
        assertFalse(ProtectionRules.flowAllowed(8, 7));
    }
}
