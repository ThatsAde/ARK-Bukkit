package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.platform.health.HealthReport;
import org.breachsmp.core.platform.health.HealthService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** {@code /breach health}: stato di database, moduli, dettagli e code (spec §14). */
public final class HealthSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.health";

    private final HealthService health;

    public HealthSubcommand(HealthService health) {
        this.health = Objects.requireNonNull(health, "health");
    }

    @Override
    public String name() {
        return "health";
    }

    @Override
    public String usage() {
        return "/breach health";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        return health.check().thenApply(HealthSubcommand::toResult);
    }

    static CommandResult toResult(HealthReport report) {
        List<String> lines = report.lines();
        List<ChatLine> chat = new ArrayList<>();
        chat.add(switch (report.overall()) {
            case HEALTHY -> ChatLine.success(lines.getFirst());
            case DEGRADED, STARTING -> ChatLine.warning(lines.getFirst());
            default -> ChatLine.error(lines.getFirst());
        });
        lines.stream().skip(1).map(ChatLine::muted).forEach(chat::add);
        return new CommandResult(chat, List.of());
    }
}
