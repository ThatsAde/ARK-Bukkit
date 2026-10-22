package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.api.player.PlayerService;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Parti comuni dei sottocomandi admin dell'economia. */
final class AdminCommands {

    private AdminCommands() {
    }

    static CompletableFuture<CommandResult> done(CommandResult result) {
        return CompletableFuture.completedFuture(result);
    }

    /** Cerca il giocatore per nome; se non c'è risponde con un errore, altrimenti prosegue con {@code action}. */
    static CompletableFuture<CommandResult> withPlayer(PlayerService players, String name,
                                                       Function<PlayerProfile, CompletableFuture<CommandResult>> action) {
        return players.findByName(name).thenCompose(found -> found
                .map(action)
                .orElseGet(() -> done(CommandResult.error("Giocatore non trovato: " + name + "."))));
    }

    static List<String> playersByPrefix(String prefix, List<String> onlinePlayers) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return onlinePlayers.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
