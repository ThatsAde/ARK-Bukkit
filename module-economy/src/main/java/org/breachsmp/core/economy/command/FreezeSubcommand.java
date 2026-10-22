package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.economy.admin.EconomyAdmin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** {@code /breach freeze|unfreeze <giocatore> <motivo>}: congela o sblocca il conto della stagione corrente (spec §14, E13). */
public final class FreezeSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.freeze";

    private final boolean freeze;
    private final EconomyAdmin admin;
    private final PlayerService players;

    /** @param freeze {@code true} per {@code /breach freeze}, {@code false} per {@code /breach unfreeze} */
    public FreezeSubcommand(boolean freeze, EconomyAdmin admin, PlayerService players) {
        this.freeze = freeze;
        this.admin = Objects.requireNonNull(admin, "admin");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public String name() {
        return freeze ? "freeze" : "unfreeze";
    }

    @Override
    public String usage() {
        return "/breach " + name() + " <giocatore> <motivo>";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        AdminArgs.Target target;
        try {
            target = AdminArgs.target(invocation.args());
        } catch (IllegalArgumentException e) {
            return AdminCommands.done(CommandResult.error(e.getMessage()));
        }
        return AdminCommands.withPlayer(players, target.player(), profile -> {
            CompletableFuture<Boolean> operation = freeze
                    ? admin.freeze(invocation.senderId(), profile, target.reason())
                    : admin.unfreeze(invocation.senderId(), profile, target.reason());
            return operation.thenApply(changed -> {
                if (freeze) {
                    return changed
                            ? CommandResult.of(ChatLine.success("Conto di " + profile.name() + " congelato."))
                            : CommandResult.of(ChatLine.warning("Il conto di " + profile.name() + " era già congelato."));
                }
                return changed
                        ? CommandResult.of(ChatLine.success("Conto di " + profile.name() + " sbloccato."))
                        : CommandResult.of(ChatLine.warning("Il conto di " + profile.name() + " non era congelato."));
            });
        }).exceptionally(error -> CommandResult.error(EconomyMessages.failure(error, null)));
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        return args.size() == 1 ? AdminCommands.playersByPrefix(args.getFirst(), onlinePlayers) : List.of();
    }
}
