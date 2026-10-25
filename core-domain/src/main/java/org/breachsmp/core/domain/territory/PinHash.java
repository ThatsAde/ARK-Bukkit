package org.breachsmp.core.domain.territory;

import java.util.Arrays;
import java.util.Objects;

/** Hash di un PIN con il suo sale (D-37): il PIN in chiaro non si salva mai. Copie difensive, uguaglianza per contenuto. */
public record PinHash(byte[] salt, byte[] hash) {

    public PinHash {
        salt = Objects.requireNonNull(salt, "salt").clone();
        hash = Objects.requireNonNull(hash, "hash").clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PinHash that && Arrays.equals(salt, that.salt) && Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(salt) + Arrays.hashCode(hash);
    }

    @Override
    public String toString() {
        return "PinHash[nascosto]";
    }
}
