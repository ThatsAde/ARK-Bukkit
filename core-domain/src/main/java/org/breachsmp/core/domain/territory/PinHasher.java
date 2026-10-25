package org.breachsmp.core.domain.territory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/** Hash dei PIN dei Tool Cupboard (D-37): PBKDF2WithHmacSHA256, 60.000 iterazioni, sale casuale di 16 byte. */
public final class PinHasher {

    public static final int ITERATIONS = 60_000;

    private static final Pattern PIN = Pattern.compile("\\d{4,8}");
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    /** @throws IllegalArgumentException se il PIN non ha da 4 a 8 cifre */
    public static void validate(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("Il PIN deve avere da 4 a 8 cifre.");
        }
    }

    public PinHash hash(String pin) {
        validate(pin);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PinHash(salt, derive(pin, salt));
    }

    /** Confronto a tempo costante. */
    public boolean verify(String pin, PinHash stored) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            return false;
        }
        return MessageDigest.isEqual(derive(pin, stored.salt()), stored.hash());
    }

    private static byte[] derive(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 non disponibile nella JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
