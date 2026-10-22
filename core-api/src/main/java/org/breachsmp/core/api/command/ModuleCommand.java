package org.breachsmp.core.api.command;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Comando di primo livello di un modulo (es. {@code /pay}), senza tipi Bukkit (D-15).
 * {@link #execute} gira sul main thread e non deve bloccare: fa solo parsing e restituisce un future; l'I/O passa dai servizi.
 * Errori attesi → {@link CommandResult} con righe ERROR; un'eccezione è un errore interno.
 */
public interface ModuleCommand {

    String label();

    default List<String> aliases() {
        return List.of();
    }

    String description();

    /** Permesso richiesto per usare il comando (dichiaralo in plugin.yml). */
    String permission();

    /** Altri permessi da conoscere (es. {@code breach.balance.others}): la piattaforma li valuta e li passa in {@link CommandInvocation#grantedPermissions()}. */
    default Set<String> extraPermissions() {
        return Set.of();
    }

    /** {@code true} se la console non può usarlo. */
    default boolean playerOnly() {
        return false;
    }

    CompletableFuture<CommandResult> execute(CommandInvocation invocation);

    /** Suggerimenti per l'ultimo argomento; {@code onlinePlayers} = nomi dei giocatori online. */
    default List<String> suggest(List<String> args, List<String> onlinePlayers) {
        return List.of();
    }
}
