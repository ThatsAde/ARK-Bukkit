package org.breachsmp.core.api.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandResultTest {

    @Test
    void factoriesAndNotices() {
        UUID bob = UUID.randomUUID();
        CommandResult result = CommandResult.of(ChatLine.success("fatto")).withNotice(bob, ChatLine.info("ciao"));
        assertEquals(List.of(new ChatLine(Tone.SUCCESS, "fatto")), result.lines());
        assertEquals(List.of(new Notice(bob, new ChatLine(Tone.INFO, "ciao"))), result.notices());
        assertEquals(List.of(new ChatLine(Tone.ERROR, "no")), CommandResult.error("no").lines());
    }

    @Test
    void invocationExposesPermissionsAndSenderKind() {
        var console = new CommandInvocation(null, "CONSOLE", List.of("a"), Set.of("breach.x"));
        assertFalse(console.isPlayer());
        assertTrue(console.has("breach.x"));
        assertFalse(console.has("breach.y"));
        assertThrows(UnsupportedOperationException.class, () -> console.args().add("b"));
    }
}
