package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinHasherTest {

    @Test
    void validPinsHashAndVerify() {
        PinHasher hasher = new PinHasher();
        PinHash hash = hasher.hash("4821");
        assertTrue(hasher.verify("4821", hash));
        assertFalse(hasher.verify("4822", hash));
        assertNotEquals(hasher.hash("4821"), hash, "il sale deve cambiare");
        assertEquals(hash, new PinHash(hash.salt(), hash.hash()), "uguaglianza per contenuto");
    }

    @Test
    void pinMustBeFourToEightDigits() {
        for (String bad : List.of("123", "123456789", "12a4", " 1234", "")) {
            assertEquals("Il PIN deve avere da 4 a 8 cifre.",
                    assertThrows(IllegalArgumentException.class, () -> PinHasher.validate(bad)).getMessage(), bad);
        }
        PinHasher.validate("0000");
        PinHasher.validate("12345678");
    }
}
