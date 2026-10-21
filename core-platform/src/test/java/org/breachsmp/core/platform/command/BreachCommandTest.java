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

    @Test
    void describeUnwrapsCompletionException() {
        var wrapped = new java.util.concurrent.CompletionException(new IllegalStateException("DB irraggiungibile"));
        assertEquals("DB irraggiungibile", BreachCommand.describe(wrapped));
        assertEquals("IllegalStateException", BreachCommand.describe(new IllegalStateException()));
        assertEquals("boom", BreachCommand.describe(new RuntimeException("boom")));
    }
}
