package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreachCommandTest {

    private static AdminSubcommand sub(String name, String permission) {
        return new AdminSubcommand() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String usage() {
                return "/breach " + name;
            }

            @Override
            public String permission() {
                return permission;
            }

            @Override
            public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
                return CompletableFuture.completedFuture(CommandResult.of(ChatLine.info(name)));
            }

            @Override
            public List<String> suggest(List<String> args, List<String> online) {
                return List.of("arg-" + args.size());
            }
        };
    }

    private static final List<AdminSubcommand> SUBS = List.of(sub("health", "breach.admin.health"), sub("eco", "breach.admin.eco"));

    @Test
    void suggestsOnlyPermittedSubcommandsByPrefix() {
        Predicate<String> onlyHealth = "breach.admin.health"::equals;
        assertEquals(List.of("health"), BreachCommand.suggestions(List.of(), SUBS, onlyHealth, List.of()));
        assertEquals(List.of("health"), BreachCommand.suggestions(List.of("HE"), SUBS, onlyHealth, List.of()));
        assertEquals(List.of(), BreachCommand.suggestions(List.of("e"), SUBS, onlyHealth, List.of()));
    }

    @Test
    void deeperArgumentsAreDelegatedOnlyWithPermission() {
        assertEquals(List.of("arg-1"), BreachCommand.suggestions(List.of("eco", ""), SUBS, p -> true, List.of()));
        assertEquals(List.of(), BreachCommand.suggestions(List.of("eco", ""), SUBS, p -> false, List.of()));
        assertEquals(List.of(), BreachCommand.suggestions(List.of("boh", ""), SUBS, p -> true, List.of()));
    }

    @Test
    void usageListsVisibleSubcommands() {
        assertEquals("Uso: /breach <health|eco>", BreachCommand.usage(SUBS));
        assertEquals("Nessun sottocomando disponibile.", BreachCommand.usage(List.of()));
    }
}
