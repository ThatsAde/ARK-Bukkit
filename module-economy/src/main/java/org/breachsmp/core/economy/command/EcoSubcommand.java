package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.domain.economy.Money;
import org.breachsmp.core.economy.admin.EconomyAdmin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** {@code /breach eco <give|take> <giocatore> <importo> <motivo>} (spec §14, E20). */
public final class EcoSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.eco";

    private final EconomyAdmin admin;
    private final PlayerService players;

    public EcoSubcommand(EconomyAdmin admin, PlayerService players) {
        this.admin = Objects.requireNonNull(admin, "admin");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public String name() {
        return "eco";
    }

    @Override
    public String usage() {
        return AdminArgs.ECO_USAGE;
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        AdminArgs.Eco eco;
        try {
            eco = AdminArgs.eco(invocation.args());
        } catch (IllegalArgumentException e) {
            return AdminCommands.done(CommandResult.error(e.getMessage()));
        }
        return AdminCommands.withPlayer(players, eco.player(), target -> {
            CompletableFuture<TxResult> operation = eco.give()
                    ? admin.give(invocation.senderId(), target, eco.amount(), eco.reason())
                    : admin.take(invocation.senderId(), target, eco.amount(), eco.reason());
            return operation.handle((result, error) -> {
                if (error != null) {
                    return CommandResult.error(EconomyMessages.failure(error, AccountRef.player(target.uuid())));
                }
                String verb = eco.give() ? "Dati " : "Tolti ";
                return CommandResult.of(ChatLine.success(verb + Money.format(eco.amount()) + " a " + target.name()
                        + " (tx #" + result.txId() + ")."));
            });
        }).exceptionally(error -> CommandResult.error(EconomyMessages.failure(error, null)));
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        if (args.size() == 1) {
            return List.of("give", "take").stream().filter(option -> option.startsWith(args.getFirst().toLowerCase())).toList();
        }
        if (args.size() == 2) {
            return AdminCommands.playersByPrefix(args.get(1), onlinePlayers);
        }
        return List.of();
    }
}
