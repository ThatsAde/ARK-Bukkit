package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.ModuleCommand;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.LedgerService;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.domain.economy.Money;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** {@code /balance [giocatore]} (alias {@code /bal}): il proprio saldo; quello di altri con {@code breach.balance.others}. */
public final class BalanceCommand implements ModuleCommand {

    public static final String PERMISSION = "breach.balance";
    public static final String PERMISSION_OTHERS = "breach.balance.others";

    private final LedgerService ledger;
    private final PlayerService players;

    public BalanceCommand(LedgerService ledger, PlayerService players) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public String label() {
        return "balance";
    }

    @Override
    public List<String> aliases() {
        return List.of("bal");
    }

    @Override
    public String description() {
        return "Mostra il saldo";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public Set<String> extraPermissions() {
        return Set.of(PERMISSION_OTHERS);
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        List<String> args = invocation.args();
        if (args.isEmpty()) {
            if (!invocation.isPlayer()) {
                return done(CommandResult.error("Dalla console usa /balance <giocatore>."));
            }
            return ledger.balance(AccountRef.player(invocation.senderId()))
                    .thenApply(balance -> CommandResult.of(ChatLine.info("Saldo: " + Money.format(balance))))
                    .exceptionally(error -> CommandResult.error(EconomyMessages.failure(error, null)));
        }
        if (args.size() > 1) {
            return done(CommandResult.error("Uso: /balance [giocatore]"));
        }
        if (!invocation.has(PERMISSION_OTHERS)) {
            return done(CommandResult.error("Non hai il permesso di vedere il saldo di altri giocatori."));
        }
        String name = args.getFirst();
        return players.findByName(name).thenCompose(found -> found
                .map(target -> ledger.balance(AccountRef.player(target.uuid()))
                        .thenApply(balance -> CommandResult.of(ChatLine.info("Saldo di " + target.name() + ": " + Money.format(balance)))))
                .orElseGet(() -> done(CommandResult.error("Giocatore non trovato: " + name + " (deve essere entrato almeno una volta)."))))
                .exceptionally(error -> CommandResult.error(EconomyMessages.failure(error, null)));
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        if (args.size() != 1) {
            return List.of();
        }
        String prefix = args.getFirst().toLowerCase(Locale.ROOT);
        return onlinePlayers.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static CompletableFuture<CommandResult> done(CommandResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
