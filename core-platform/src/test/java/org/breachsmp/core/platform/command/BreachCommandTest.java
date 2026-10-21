package org.breachsmp.core.platform.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreachCommandTest {

    @Test
    void suggestsSubcommandsByPrefix() {
        assertEquals(List.of("health"), BreachCommand.suggestions(new String[]{}));
        assertEquals(List.of("health"), BreachCommand.suggestions(new String[]{"HE"}));
        assertEquals(List.of(), BreachCommand.suggestions(new String[]{"x"}));
        assertEquals(List.of(), BreachCommand.suggestions(new String[]{"health", "x"}));
    }
}
