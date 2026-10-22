package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.domain.economy.Money;
import org.breachsmp.core.economy.admin.EconomyAdmin;
import org.breachsmp.core.economy.ledger.HistoryRow;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** {@code /breach tx <giocatore> [pagina]}: movimenti del giocatore nella stagione corrente, dal più recente (spec §14). */
public final class TxSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.tx";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final EconomyAdmin admin;
    private final PlayerService players;

    public TxSubcommand(EconomyAdmin admin, PlayerService players) {
        this.admin = Objects.requireNonNull(admin, "admin");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public String name() {
        return "tx";
    }

    @Override
    public String usage() {
        return "/breach tx <giocatore> [pagina]";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        List<String> args = invocation.args();
        if (args.isEmpty() || args.size() > 2) {
            return AdminCommands.done(CommandResult.error("Uso: " + usage()));
        }
        int page;
        try {
            page = AdminArgs.page(args);
        } catch (IllegalArgumentException e) {
            return AdminCommands.done(CommandResult.error(e.getMessage()));
        }
        return AdminCommands.withPlayer(players, args.getFirst(), target -> admin.history(target.uuid(), page)
                .thenApply(rows -> render(target.name(), page, rows)))
                .exceptionally(error -> CommandResult.error(EconomyMessages.failure(error, null)));
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        return args.size() == 1 ? AdminCommands.playersByPrefix(args.getFirst(), onlinePlayers) : List.of();
    }

    private static CommandResult render(String name, int page, List<HistoryRow> rows) {
        List<ChatLine> lines = new ArrayList<>();
        lines.add(ChatLine.info("Movimenti di " + name + " – pagina " + page));
        if (rows.isEmpty()) {
            lines.add(ChatLine.info("Nessun movimento."));
        }
        for (HistoryRow row : rows) {
            String delta = (row.delta() > 0 ? "+" : "") + Money.format(row.delta());
            lines.add(ChatLine.muted("#" + row.txId() + " " + WHEN.format(row.createdAt()) + " " + row.kind() + " " + delta
                    + " → " + Money.format(row.balanceAfter())));
        }
        return new CommandResult(lines, List.of());
    }
}
