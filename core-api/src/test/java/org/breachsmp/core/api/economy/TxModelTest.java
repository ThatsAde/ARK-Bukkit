package org.breachsmp.core.api.economy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxModelTest {

    @Test
    void requestCopiesItsCollectionsAndAllowsNullMetaAndActor() {
        List<Leg> legs = new ArrayList<>(List.of(new Leg(AccountRef.system(SystemAccount.FAUCET), -5),
                new Leg(AccountRef.player(UUID.randomUUID()), 5)));
        TxRequest request = new TxRequest(UUID.randomUUID(), TxKinds.ADMIN_GIVE, legs, null, null);
        legs.clear();
        assertEquals(2, request.legs().size());
        assertEquals(Map.of(), request.meta());
        assertThrows(NullPointerException.class, () -> new TxRequest(null, TxKinds.PAY, List.of(), Map.of(), null));
    }

    @Test
    void resultFindsBalanceAfterPerAccount() {
        AccountRef alice = AccountRef.player(UUID.randomUUID());
        TxResult result = new TxResult(7, UUID.randomUUID(), TxKinds.PAY, Instant.EPOCH,
                List.of(new EntryView(alice, -100, 900)), false);
        assertEquals(OptionalLong.of(900), result.balanceAfter(alice));
        assertEquals(OptionalLong.empty(), result.balanceAfter(AccountRef.player(UUID.randomUUID())));
    }

    @Test
    void kindsAndFreezeBypass() {
        assertTrue(TxKinds.KNOWN.containsAll(Set.of("PAY", "ADMIN_GIVE", "ADMIN_TAKE", "ROLLBACK", "STARTING_BALANCE")));
        assertFalse(TxKinds.bypassesFreeze(TxKinds.PAY));
        assertTrue(TxKinds.bypassesFreeze(TxKinds.ROLLBACK));
    }
}
