package org.breachsmp.core.platform.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.ModuleCommand;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Adatta un {@link ModuleCommand} di un modulo al {@link BasicCommand} di Paper (D-15). */
public final class ModuleCommandAdapter implements BasicCommand {

    private final ModuleCommand command;
    private final CommandDelivery delivery;
    private final Server server;

    public ModuleCommandAdapter(ModuleCommand command, CommandDelivery delivery, Server server) {
        this.command = Objects.requireNonNull(command, "command");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (command.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(ChatRenderer.render(ChatLine.error("Solo un giocatore può usare questo comando.")));
            return;
        }
        CommandInvocation invocation = CommandDelivery.invocation(sender, List.of(args), command.extraPermissions());
        delivery.deliver(sender, () -> command.execute(invocation));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return command.suggest(List.of(args), CommandDelivery.onlinePlayerNames(server));
    }

    @Override
    public String permission() {
        return command.permission();
    }
}
