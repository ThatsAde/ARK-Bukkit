package org.breachsmp.core.domain.economy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void parsesCommaOrDotWithUpToTwoDecimals() {
        assertEquals(1250, Money.parse("12,50"));
        assertEquals(1250, Money.parse("12.5"));
        assertEquals(100_000, Money.parse("1000"));
        assertEquals(700, Money.parse(" 7 "));
        assertEquals(1, Money.parse("0,01"));
    }

    @Test
    void rejectsMalformedAmounts() {
        for (String text : List.of("", " ", "abc", "-5", "1e3", "12,345", "1.000,50", "12.5.3", "0", "0,00", "9999999999999999", "+5")) {
            assertThrows(IllegalArgumentException.class, () -> Money.parse(text), text);
        }
        assertEquals("L'importo deve essere maggiore di zero",
                assertThrows(IllegalArgumentException.class, () -> Money.parse("0")).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Money.parse("abc")).getMessage()
                .startsWith("Importo non valido: 'abc'"));
    }

    @Test
    void formatsWithThousandsSeparatorAndDecimalsOnlyWhenNeeded() {
        assertEquals("0 monete", Money.format(0));
        assertEquals("1 moneta", Money.format(100));
        assertEquals("0,05 monete", Money.format(5));
        assertEquals("12,50 monete", Money.format(1250));
        assertEquals("1.234 monete", Money.format(123_400));
        assertEquals("1.234,56 monete", Money.format(123_456));
        assertEquals("1.000.000 monete", Money.format(100_000_000));
        assertEquals("-12,50 monete", Money.format(-1250));
    }
}
