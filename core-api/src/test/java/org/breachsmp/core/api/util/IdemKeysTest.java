package org.breachsmp.core.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdemKeysTest {

    @Test
    void sameTextSameKeyDifferentTextDifferentKey() {
        assertEquals(IdemKeys.of("recon:2026-10-22"), IdemKeys.of("recon:2026-10-22"));
        assertNotEquals(IdemKeys.of("rollback:1"), IdemKeys.of("rollback:2"));
        assertEquals(3, IdemKeys.of("x").version());
    }

    @Test
    void rejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> IdemKeys.of(" "));
    }
}
