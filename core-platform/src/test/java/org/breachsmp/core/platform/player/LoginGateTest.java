package org.breachsmp.core.platform.player;

import org.breachsmp.core.api.data.DataAccessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LoginGateTest {

    private static final Logger LOGGER = Logger.getLogger("test");
    private final PlayerHookRegistry hooks = new PlayerHookRegistry(LOGGER);

    @Test
    void admittedPlayerIsUpsertedBeforeHooks() {
        List<String> calls = new ArrayList<>();
        hooks.onLogin("economy", (uuid, name) -> calls.add("hook"));
        LoginGate gate = new LoginGate((uuid, name) -> calls.add("upsert:" + name), hooks, LOGGER);
        assertEquals(Optional.empty(), gate.admit(UUID.randomUUID(), "Alice"));
        assertEquals(List.of("upsert:Alice", "hook"), calls);
    }

    @Test
    void upsertFailureRejectsAndSkipsHooks() {
        AtomicBoolean hookCalled = new AtomicBoolean();
        hooks.onLogin("economy", (uuid, name) -> hookCalled.set(true));
        LoginGate gate = new LoginGate((uuid, name) -> {
            throw new DataAccessException("DB giù", null);
        }, hooks, LOGGER);
        assertEquals(Optional.of(LoginGate.KICK_MESSAGE), gate.admit(UUID.randomUUID(), "Alice"));
        assertFalse(hookCalled.get());
    }

    @Test
    void hookFailureRejects() {
        hooks.onLogin("economy", (uuid, name) -> {
            throw new IllegalStateException("conto non caricato");
        });
        LoginGate gate = new LoginGate((uuid, name) -> {
        }, hooks, LOGGER);
        assertEquals(Optional.of(LoginGate.KICK_MESSAGE), gate.admit(UUID.randomUUID(), "Alice"));
    }
}
