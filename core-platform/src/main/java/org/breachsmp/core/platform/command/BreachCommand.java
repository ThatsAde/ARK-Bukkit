package org.breachsmp.core.platform.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.breachsmp.core.platform.health.HealthReport;
import org.breachsmp.core.platform.health.HealthService;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** {@code /breach <sottocomando>}: strumenti di amministrazione (spec §14). In M0: {@code health}. */
public final class BreachCommand implements BasicCommand {

    public static final String PERMISSION = "breach.command";
    public static final String PERMISSION_HEALTH = "breach.admin.health";

    private static final List<String> SUBCOMMANDS = List.of("health");

    private final HealthService health;
    private final Executor main;

    public BreachCommand(HealthService health, Executor main) {
        this.health = Objects.requireNonNull(health, "health");
        this.main = Objects.requireNonNull(main, "main");
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 1 && args[0].equalsIgnoreCase("health")) {
            health(sender);
            return;
        }
        sender.sendMessage(Component.text("Uso: /breach health", NamedTextColor.YELLOW));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return suggestions(args);
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    static List<String> suggestions(String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(subcommand -> subcommand.startsWith(prefix)).toList();
    }

    private void health(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_HEALTH)) {
            sender.sendMessage(Component.text("Non hai il permesso per questo comando.", NamedTextColor.RED));
            return;
        }
        // Il controllo gira sul thread DB; la risposta torna sul main (I2)
        health.check().whenCompleteAsync((report, error) -> {
            if (error != null) {
                sender.sendMessage(Component.text("Health check fallito: " + describe(error), NamedTextColor.RED));
                return;
            }
            send(sender, report);
        }, main);
    }

    /** Testo leggibile dell'errore: scarta il wrapper {@link CompletionException}; senza messaggio usa il nome della classe. */
    static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private static void send(CommandSender sender, HealthReport report) {
        NamedTextColor color = switch (report.overall()) {
            case HEALTHY -> NamedTextColor.GREEN;
            case DEGRADED, STARTING -> NamedTextColor.YELLOW;
            default -> NamedTextColor.RED;
        };
        List<String> lines = report.lines();
        sender.sendMessage(Component.text(lines.getFirst(), color));
        lines.stream().skip(1).forEach(line -> sender.sendMessage(Component.text(line, NamedTextColor.GRAY)));
    }
}
