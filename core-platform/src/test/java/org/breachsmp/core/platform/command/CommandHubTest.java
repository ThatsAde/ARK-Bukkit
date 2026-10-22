package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.ModuleCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandHubTest {

    private final CommandHub hub = new CommandHub();

    @Test
    void rejectsDuplicatesReservedNamesAndLateRegistrations() {
        hub.register(command("balance", List.of("bal")));
        assertThrows(IllegalStateException.class, () -> hub.register(command("bal", List.of())));
        assertThrows(IllegalStateException.class, () -> hub.register(command("money", List.of("balance"))));
        assertThrows(IllegalArgumentException.class, () -> hub.register(command("Pay!", List.of())));
        assertThrows(IllegalStateException.class, () -> hub.registerAdmin(subcommand("health")));
        hub.registerAdmin(subcommand("eco"));
        assertThrows(IllegalStateException.class, () -> hub.registerAdmin(subcommand("eco")));
        hub.close();
        assertThrows(IllegalStateException.class, () -> hub.register(command("pay", List.of())));
        assertEquals(List.of("balance"), hub.commands().stream().map(ModuleCommand::label).toList());
        assertEquals(List.of("eco"), hub.adminSubcommands().stream().map(AdminSubcommand::name).toList());
    }

    private static ModuleCommand command(String label, List<String> aliases) {
        return new ModuleCommand() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public List<String> aliases() {
                return aliases;
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public String permission() {
                return "breach.test";
            }

            @Override
            public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
                return CompletableFuture.completedFuture(CommandResult.of());
            }
        };
    }

    private static AdminSubcommand subcommand(String name) {
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
                return "breach.test";
            }

            @Override
            public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
                return CompletableFuture.completedFuture(CommandResult.of());
            }
        };
    }
}
