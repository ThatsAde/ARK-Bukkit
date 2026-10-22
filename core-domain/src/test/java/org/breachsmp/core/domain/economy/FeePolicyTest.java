package org.breachsmp.core.domain.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeePolicyTest {

    @Test
    void feeIsCeilingAndNeverZeroOnMicroTransactions() {
        assertEquals(1, FeePolicy.fee(1, 1));
        assertEquals(500, FeePolicy.fee(10_000, 500));
        assertEquals(501, FeePolicy.fee(10_001, 500));
        assertEquals(0, FeePolicy.fee(0, 500));
        assertEquals(0, FeePolicy.fee(100, 0));
        assertEquals(new FeePolicy.Split(501, 9_500), FeePolicy.split(10_001, 500));
    }

    @Test
    void rejectsOutOfRangeInput() {
        assertThrows(IllegalArgumentException.class, () -> FeePolicy.fee(-1, 100));
        assertThrows(IllegalArgumentException.class, () -> FeePolicy.fee(100, 10_001));
        assertThrows(IllegalArgumentException.class, () -> FeePolicy.fee(100, -1));
    }
}
