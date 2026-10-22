package org.breachsmp.core.economy.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminArgsTest {

    @Test
    void ecoParsesOperationAmountAndMultiWordReason() {
        assertEquals(new AdminArgs.Eco(true, "Bob", 1250, "premio evento"), AdminArgs.eco(List.of("give", "Bob", "12,50", "premio", "evento")));
        assertEquals(new AdminArgs.Eco(false, "Bob", 100, "x"), AdminArgs.eco(List.of("TAKE", "Bob", "1", "x")));
    }

    @Test
    void ecoRejectsMissingPartsWithUsage() {
        assertEquals("Uso: /breach eco <give|take> <giocatore> <importo> <motivo>",
                assertThrows(IllegalArgumentException.class, () -> AdminArgs.eco(List.of("give", "Bob", "5"))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> AdminArgs.eco(List.of("steal", "Bob", "5", "x")));
        assertThrows(IllegalArgumentException.class, () -> AdminArgs.eco(List.of("give", "Bob", "-5", "x")));
    }

    @Test
    void rollbackNeedsTheTxKeywordAndANumericId() {
        assertEquals(new AdminArgs.Rollback(42, "doppio accredito"), AdminArgs.rollback(List.of("tx", "42", "doppio", "accredito")));
        assertThrows(IllegalArgumentException.class, () -> AdminArgs.rollback(List.of("42", "x")));
        assertEquals("Id transazione non valido: abc",
                assertThrows(IllegalArgumentException.class, () -> AdminArgs.rollback(List.of("tx", "abc", "x"))).getMessage());
    }

    @Test
    void reasonAndPageRules() {
        assertEquals("Specifica un motivo.", assertThrows(IllegalArgumentException.class, () -> AdminArgs.target(List.of("Bob"))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> AdminArgs.reason(List.of("x".repeat(201)), 0));
        assertEquals(1, AdminArgs.page(List.of("Bob")));
        assertEquals(3, AdminArgs.page(List.of("Bob", "3")));
        assertThrows(IllegalArgumentException.class, () -> AdminArgs.page(List.of("Bob", "0")));
    }
}
