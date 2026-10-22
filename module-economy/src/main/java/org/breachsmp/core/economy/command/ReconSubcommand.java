package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.economy.recon.ReconciliationJob;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** {@code /breach recon}: esegue subito la riconciliazione (D-27). */
public final class ReconSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.recon";

    private final ReconciliationJob recon;
    private final Executor db;

    public ReconSubcommand(ReconciliationJob recon, Executor db) {
        this.recon = Objects.requireNonNull(recon, "recon");
        this.db = Objects.requireNonNull(db, "db");
    }

    @Override
    public String name() {
        return "recon";
    }

    @Override
    public String usage() {
        return "/breach recon";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        return CompletableFuture.supplyAsync(recon::runNow, db).thenApply(report -> {
            List<ChatLine> lines = recon.healthLines().stream()
                    .map(line -> report.clean() ? ChatLine.success(line) : ChatLine.error(line))
                    .toList();
            return new CommandResult(lines, List.of());
        });
    }
}
