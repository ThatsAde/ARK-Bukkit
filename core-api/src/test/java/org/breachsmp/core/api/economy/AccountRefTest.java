package org.breachsmp.core.api.economy;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRefTest {

    @Test
    void systemAccountsHaveFixedRefsAndOnlyFaucetGoesNegative() {
        assertEquals(new UUID(0, 1), SystemAccount.FAUCET.ref());
        assertEquals(Optional.of(SystemAccount.SINK), SystemAccount.byRef(new UUID(0, 2)));
        assertTrue(AccountRef.system(SystemAccount.FAUCET).mayGoNegative());
        assertFalse(AccountRef.system(SystemAccount.SINK).mayGoNegative());
        assertFalse(AccountRef.player(UUID.randomUUID()).mayGoNegative());
    }

    @Test
    void systemRefMustBeAKnownSystemAccount() {
        assertThrows(IllegalArgumentException.class, () -> new AccountRef(OwnerType.SYSTEM, UUID.randomUUID()));
        assertEquals("sistema:TAX_POOL", AccountRef.system(SystemAccount.TAX_POOL).toString());
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals("giocatore:" + uuid, AccountRef.player(uuid).toString());
    }
}
