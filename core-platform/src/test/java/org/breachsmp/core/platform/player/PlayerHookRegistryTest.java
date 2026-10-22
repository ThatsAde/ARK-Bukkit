package org.breachsmp.core.platform.player;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHookRegistryTest {

    private final PlayerHookRegistry registry = new PlayerHookRegistry(Logger.getLogger("test"));

    @Test
    void loginHooksRunInOrderAndTheFirstFailureStopsTheChain() {
        List<String> calls = new ArrayList<>();
        registry.onLogin("a", (uuid, name) -> calls.add("a:" + name));
        registry.onLogin("b", (uuid, name) -> {
            throw new IOException("DB lento");
        });
        registry.onLogin("c", (uuid, name) -> calls.add("c"));
        LoginRejectedException error = assertThrows(LoginRejectedException.class, () -> registry.runLogin(UUID.randomUUID(), "Alice"));
        assertEquals(List.of("a:Alice"), calls);
        assertTrue(error.getMessage().contains("'b'"));
    }

    @Test
    void aFailingQuitHookDoesNotStopTheOthers() {
        List<String> calls = new ArrayList<>();
        registry.onQuit("a", uuid -> {
            throw new IllegalStateException("x");
        });
        registry.onQuit("b", uuid -> calls.add("b"));
        registry.runQuit(UUID.randomUUID());
        assertEquals(List.of("b"), calls);
    }
}
