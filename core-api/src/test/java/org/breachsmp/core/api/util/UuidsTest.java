package org.breachsmp.core.api.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UuidsTest {

    @Test
    void roundTripsThroughSixteenBytes() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] bytes = Uuids.toBytes(uuid);
        assertEquals(16, bytes.length);
        assertEquals(0x12, bytes[0]);
        assertEquals(uuid, Uuids.fromBytes(bytes));
    }

    @Test
    void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> Uuids.fromBytes(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> Uuids.fromBytes(null));
    }
}
