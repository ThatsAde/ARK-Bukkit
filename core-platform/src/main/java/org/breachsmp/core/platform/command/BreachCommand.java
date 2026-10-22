package org.breachsmp.core.platform.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** {@code /breach <sottocomando>}: strumenti di amministrazione (spec §14), della piattaforma e dei moduli. */
public final class BreachCommand implements BasicCommand {

    public static final String PERMISSION = "breach.command";

    private final Map<String, AdminSubcommand> subcommands = new LinkedHashMap<>();
    private final CommandDelivery delivery;
    private final Server server;

    public BreachCommand(Collection<AdminSubcommand> subcommands, CommandDelivery delivery, Server server) {
        subcommands.forEach(sub -> this.subcommands.put(sub.name().toLowerCase(Locale.ROOT), sub));
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        AdminSubcommand sub = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            List<AdminSubcommand> visible = subcommands.values().stream()
                    .filter(candidate -> sender.hasPermission(candidate.permission())).toList();
            sender.sendMessage(ChatRenderer.render(ChatLine.warning(usage(visible))));
            return;
        }
        if (!sender.hasPermission(sub.permission())) {
            sender.sendMessage(ChatRenderer.render(ChatLine.error("Non hai il permesso per questo comando.")));
            return;
        }
        List<String> rest = List.of(args).subList(1, args.length);
        CommandInvocation invocation = CommandDelivery.invocation(sender, rest, sub.extraPermissions());
        delivery.deliver(sender, () -> sub.execute(invocation));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return suggestions(List.of(args), subcommands.values(), source.getSender()::hasPermission,
                CommandDelivery.onlinePlayerNames(server));
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    /** Un argomento → nomi permessi con quel prefisso; di più → suggerimenti del sottocomando, se esiste ed è permesso. */
    static List<String> suggestions(List<String> args, Collection<AdminSubcommand> subcommands, Predicate<String> has,
                                    List<String> online) {
        if (args.size() <= 1) {
            String prefix = args.isEmpty() ? "" : args.getFirst().toLowerCase(Locale.ROOT);
            return subcommands.stream()
                    .filter(sub -> has.test(sub.permission()))
                    .map(AdminSubcommand::name)
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        String name = args.getFirst().toLowerCase(Locale.ROOT);
        return subcommands.stream()
                .filter(sub -> sub.name().equals(name) && has.test(sub.permission()))
                .findFirst()
                .map(sub -> sub.suggest(args.subList(1, args.size()), online))
                .orElse(List.of());
    }

    static String usage(Collection<AdminSubcommand> visible) {
        if (visible.isEmpty()) {
            return "Nessun sottocomando disponibile.";
        }
        return "Uso: /breach <" + visible.stream().map(AdminSubcommand::name).collect(Collectors.joining("|")) + ">";
    }
}
