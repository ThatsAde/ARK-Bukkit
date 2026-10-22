package org.breachsmp.core.platform.testkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDatabaseTest {

    @Test
    void schemaEndingWithItIsDisposable() {
        assertTrue(TestDatabase.isDisposableSchema("jdbc:mariadb://localhost:3307/breach_it"));
        assertTrue(TestDatabase.isDisposableSchema("jdbc:mariadb://localhost:3307/breach_it?useSsl=false"));
    }

    @Test
    void otherSchemasAreProtected() {
        assertFalse(TestDatabase.isDisposableSchema("jdbc:mariadb://localhost:3306/breach_game"));
        assertFalse(TestDatabase.isDisposableSchema("jdbc:mariadb://localhost:3306/"));
        assertFalse(TestDatabase.isDisposableSchema("jdbc:mariadb://localhost:3306"));
        assertFalse(TestDatabase.isDisposableSchema("jdbc:mariadb://host_it:3306/breach_game"));
    }
}
