package org.breachsmp.core.domain.territory;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.logging.Logger;

import static org.breachsmp.core.domain.territory.ClaimBoxTest.W;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Budget della spec §14: lookup di un claim ≤ 50 µs. */
class ClaimIndexPerformanceTest {

    private static final int AREA = 4_000;

    @Test
    void lookupStaysUnderFiftyMicrosecondsWithFiveThousandClaims() {
        Random random = new Random(7);
        ClaimIndex<Integer> index = new ClaimIndex<>();
        for (int i = 0; i < 5_000; i++) {
            index.add(i, new ClaimBox(W, random.nextInt(AREA) - AREA / 2, 40 + random.nextInt(80),
                    random.nextInt(AREA) - AREA / 2, 16));
        }
        long sink = 0;
        for (int i = 0; i < 20_000; i++) {
            sink += lookup(index, random);
        }
        int lookups = 200_000;
        long start = System.nanoTime();
        for (int i = 0; i < lookups; i++) {
            sink += lookup(index, random);
        }
        long meanNanos = (System.nanoTime() - start) / lookups;
        Logger.getLogger("ClaimIndexPerformanceTest").info("Lookup medio: " + meanNanos + " ns (controllo " + sink + ")");
        assertTrue(meanNanos < 50_000, "media " + meanNanos + " ns");
    }

    private static int lookup(ClaimIndex<Integer> index, Random random) {
        return index.covering(W, random.nextInt(AREA) - AREA / 2, 40 + random.nextInt(80),
                random.nextInt(AREA) - AREA / 2).size();
    }
}
