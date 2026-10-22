package org.breachsmp.core.platform.command;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandDeliveryTest {

    @Test
    void describeUnwrapsCompletionException() {
        assertEquals("DB irraggiungibile",
                CommandDelivery.describe(new CompletionException(new IllegalStateException("DB irraggiungibile"))));
        assertEquals("IllegalStateException", CommandDelivery.describe(new IllegalStateException()));
        assertEquals("boom", CommandDelivery.describe(new RuntimeException("boom")));
    }

    @Test
    void grantedKeepsOnlyHeldPermissions() {
        assertEquals(Set.of("a"), CommandDelivery.granted(Set.of("a", "b"), "a"::equals));
    }
}
