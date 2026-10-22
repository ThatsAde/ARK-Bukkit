package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.economy.admin.EconomyAdmin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** {@code /breach rollback tx <id> <motivo>}: transazione compensativa, non cancella mai righe (spec §14, D-25). */
public final class RollbackSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.rollback";

    private final EconomyAdmin admin;

    public RollbackSubcommand(EconomyAdmin admin) {
        this.admin = Objects.requireNonNull(admin, "admin");
    }

    @Override
    public String name() {
        return "rollback";
    }

    @Override
    public String usage() {
        return "/breach rollback tx <id> <motivo>";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        AdminArgs.Rollback rollback;
        try {
            rollback = AdminArgs.rollback(invocation.args());
        } catch (IllegalArgumentException e) {
            return AdminCommands.done(CommandResult.error(e.getMessage()));
        }
        return admin.rollback(invocation.senderId(), rollback.txId(), rollback.reason()).handle((result, error) -> {
            if (error != null) {
                return CommandResult.error(EconomyMessages.failure(error, null));
            }
            if (result.replayed()) {
                return CommandResult.of(ChatLine.warning("Rollback già eseguito in precedenza: tx #" + result.txId() + "."));
            }
            return CommandResult.of(ChatLine.success("Rollback eseguito: tx #" + result.txId() + " annulla #" + rollback.txId() + "."));
        });
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        return args.size() == 1 ? List.of("tx") : List.of();
    }
}
