package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.Notice;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Esegue un comando dei moduli e consegna il risultato sul main thread (I2): righe al mittente, avvisi ai giocatori online. */
public final class CommandDelivery {

    private final Executor main;
    private final Server server;
    private final Logger logger;

    public CommandDelivery(Executor main, Server server, Logger logger) {
        this.main = Objects.requireNonNull(main, "main");
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void deliver(CommandSender sender, Supplier<CompletableFuture<CommandResult>> command) {
        CompletableFuture<CommandResult> future;
        try {
            future = Objects.requireNonNull(command.get(), "il comando ha restituito null");
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        future.whenCompleteAsync((result, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Comando fallito per " + sender.getName(), error);
                sender.sendMessage(ChatRenderer.render(ChatLine.error("Errore: " + describe(error))));
                return;
            }
            result.lines().forEach(line -> sender.sendMessage(ChatRenderer.render(line)));
            for (Notice notice : result.notices()) {
                Player player = server.getPlayer(notice.player());
                if (player != null) {
                    player.sendMessage(ChatRenderer.render(notice.line()));
                }
            }
        }, main);
    }

    /** Testo leggibile dell'errore: scarta il wrapper {@link CompletionException}; senza messaggio usa il nome della classe. */
    public static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    /** I permessi di {@code permissions} che il mittente possiede. */
    public static Set<String> granted(Set<String> permissions, Predicate<String> has) {
        return permissions.stream().filter(has).collect(Collectors.toUnmodifiableSet());
    }

    /** Invocazione neutra per un mittente Bukkit; i permessi extra si valutano qui, sul main thread. */
    public static CommandInvocation invocation(CommandSender sender, List<String> args, Set<String> extraPermissions) {
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        return new CommandInvocation(senderId, sender.getName(), args, granted(extraPermissions, sender::hasPermission));
    }

    public static List<String> onlinePlayerNames(Server server) {
        return server.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
