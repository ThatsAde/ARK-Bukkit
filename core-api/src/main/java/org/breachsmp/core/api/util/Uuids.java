package org.breachsmp.core.api.util;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Conversione tra {@link UUID} e le colonne {@code BINARY(16)}. */
public final class Uuids {

    private Uuids() {
    }

    public static byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("Un UUID binario deve avere 16 byte");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
