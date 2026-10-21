package org.breachsmp.core.platform.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleServiceRegistryTest {

    interface Greeter {
        String greet();
    }

    private final SimpleServiceRegistry registry = new SimpleServiceRegistry();

    @Test
    void registeredServiceCanBeFoundAndRequired() {
        Greeter greeter = () -> "ciao";
        registry.register(Greeter.class, greeter);
        assertSame(greeter, registry.require(Greeter.class));
        assertEquals(Optional.of(greeter), registry.find(Greeter.class));
    }

    @Test
    void missingServiceIsEmptyWithFind() {
        assertTrue(registry.find(Greeter.class).isEmpty());
    }

    @Test
    void missingServiceFailsWithRequire() {
        var ex = assertThrows(IllegalStateException.class, () -> registry.require(Greeter.class));
        assertTrue(ex.getMessage().contains(Greeter.class.getName()));
    }

    @Test
    void registeringTheSameTypeTwiceFails() {
        registry.register(Greeter.class, () -> "a");
        assertThrows(IllegalStateException.class, () -> registry.register(Greeter.class, () -> "b"));
    }
}
