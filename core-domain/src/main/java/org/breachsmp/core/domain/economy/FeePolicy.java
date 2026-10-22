package org.breachsmp.core.domain.economy;

/** Tassa in punti base (spec §5): {@code ceil(P * t / 10000)}, mai zero su un importo positivo con aliquota positiva. */
public final class FeePolicy {

    public static final long MAX_BASIS_POINTS = 10_000;

    /** Divisione di un importo in tassa e netto ({@code fee + net = amount}). */
    public record Split(long fee, long net) {
    }

    private FeePolicy() {
    }

    public static long fee(long amount, long basisPoints) {
        if (amount < 0) {
            throw new IllegalArgumentException("Importo negativo: " + amount);
        }
        if (basisPoints < 0 || basisPoints > MAX_BASIS_POINTS) {
            throw new IllegalArgumentException("Aliquota fuori da [0, 10000] punti base: " + basisPoints);
        }
        if (amount == 0 || basisPoints == 0) {
            return 0;
        }
        return Math.ceilDiv(Math.multiplyExact(amount, basisPoints), MAX_BASIS_POINTS);
    }

    public static Split split(long amount, long basisPoints) {
        long fee = fee(amount, basisPoints);
        return new Split(fee, amount - fee);
    }
}
