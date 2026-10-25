package org.breachsmp.core.domain.territory;

import java.util.Arrays;

/**
 * Posizioni di un chunk piazzate da giocatori (D-39), come array ordinato di interi impacchettati: è il formato salvato nel
 * {@code PersistentDataContainer} del chunk. Coordinate locali x, z in 0..15, y assoluta in -2048..2047. Non thread-safe.
 */
public final class PlacedBlockSet {

    private int[] packed;
    private int size;

    private PlacedBlockSet(int[] sorted) {
        this.packed = sorted;
        this.size = sorted.length;
    }

    /** Da un array del PDC (anche vuoto o non ordinato); i duplicati si scartano. */
    public static PlacedBlockSet fromArray(int[] packed) {
        int[] sorted = Arrays.stream(packed).sorted().distinct().toArray();
        return new PlacedBlockSet(sorted);
    }

    /** Array ordinato per il PDC. */
    public int[] toArray() {
        return Arrays.copyOf(packed, size);
    }

    /** @return {@code true} se la posizione non c'era */
    public boolean add(int localX, int y, int localZ) {
        int key = pack(localX, y, localZ);
        int index = Arrays.binarySearch(packed, 0, size, key);
        if (index >= 0) {
            return false;
        }
        int insertAt = -index - 1;
        if (size == packed.length) {
            packed = Arrays.copyOf(packed, Math.max(8, size * 2));
        }
        System.arraycopy(packed, insertAt, packed, insertAt + 1, size - insertAt);
        packed[insertAt] = key;
        size++;
        return true;
    }

    /** @return {@code true} se la posizione c'era */
    public boolean remove(int localX, int y, int localZ) {
        int index = Arrays.binarySearch(packed, 0, size, pack(localX, y, localZ));
        if (index < 0) {
            return false;
        }
        System.arraycopy(packed, index + 1, packed, index, size - index - 1);
        size--;
        return true;
    }

    public boolean contains(int localX, int y, int localZ) {
        return Arrays.binarySearch(packed, 0, size, pack(localX, y, localZ)) >= 0;
    }

    public int size() {
        return size;
    }

    /** x nei bit 16–19, z nei bit 12–15, {@code y + 2048} nei bit 0–11: sempre positivo, quindi l'ordine è stabile. */
    static int pack(int localX, int y, int localZ) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15 || y < -2048 || y > 2047) {
            throw new IllegalArgumentException("Posizione fuori dal chunk: " + localX + ", " + y + ", " + localZ);
        }
        return (localX << 16) | (localZ << 12) | (y + 2048);
    }
}
