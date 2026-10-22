package org.breachsmp.core.api.command;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Sottocomando di {@code /breach} fornito da un modulo (es. {@code /breach eco}); stesse regole di {@link ModuleCommand}.
 * Gli {@code args} di {@link #execute} escludono il nome del sottocomando.
 */
public interface AdminSubcommand {

    String name();

    /** Riga d'uso, es. {@code /breach eco <give|take> <giocatore> <importo> <motivo>}. */
    String usage();

    String permission();

    default Set<String> extraPermissions() {
        return Set.of();
    }

    CompletableFuture<CommandResult> execute(CommandInvocation invocation);

    default List<String> suggest(List<String> args, List<String> onlinePlayers) {
        return List.of();
    }
}
