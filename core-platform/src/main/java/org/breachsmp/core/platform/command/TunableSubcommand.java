package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.platform.tunable.TunableRegistry;
import org.breachsmp.core.platform.tunable.TunableView;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** {@code /breach tunable [list | get <chiave> | set <chiave> <valore>]} (spec §3). */
public final class TunableSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.tunable";

    private final TunableRegistry registry;

    public TunableSubcommand(TunableRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public String name() {
        return "tunable";
    }

    @Override
    public String usage() {
        return "/breach tunable [list | get <chiave> | set <chiave> <valore>]";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        List<String> args = invocation.args();
        String action = args.isEmpty() ? "list" : args.getFirst().toLowerCase(Locale.ROOT);
        if (action.equals("list") && args.size() <= 1) {
            List<TunableView> all = registry.list();
            if (all.isEmpty()) {
                return done(CommandResult.of(ChatLine.info("Nessun tunable registrato.")));
            }
            return done(new CommandResult(all.stream().map(view -> ChatLine.info(describe(view))).toList(), List.of()));
        }
        if (action.equals("get") && args.size() == 2) {
            return done(registry.find(args.get(1))
                    .map(view -> CommandResult.of(ChatLine.info(describe(view))))
                    .orElseGet(() -> CommandResult.error("Tunable sconosciuto: " + args.get(1))));
        }
        if (action.equals("set") && args.size() == 3) {
            String key = args.get(1);
            return registry.set(key, args.get(2), invocation.senderId()).handle((change, error) -> {
                if (error == null) {
                    return CommandResult.of(ChatLine.success("Tunable " + key + " = " + change.now().value()
                            + " (prima: " + change.previous() + ")"));
                }
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                if (cause instanceof IllegalArgumentException) {
                    return CommandResult.error("Valore non accettato: " + cause.getMessage());
                }
                throw new CompletionException(cause);
            });
        }
        return done(CommandResult.error("Uso: " + usage()));
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        if (args.size() <= 1) {
            String prefix = args.isEmpty() ? "" : args.getFirst().toLowerCase(Locale.ROOT);
            return List.of("list", "get", "set").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.size() == 2) {
            return registry.list().stream().map(TunableView::key).filter(key -> key.startsWith(args.get(1))).toList();
        }
        return List.of();
    }

    static String describe(TunableView view) {
        return view.key() + " = " + view.value() + " (default " + view.defaultValue() + ", intervallo [" + view.min()
                + ", " + view.max() + "]) – " + view.description();
    }

    private static CompletableFuture<CommandResult> done(CommandResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
