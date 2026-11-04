package org.breachsmp.core.territory.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.territory.block.PlacedBlockTracker;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.claim.LiveClaim;
import org.breachsmp.core.territory.cupboard.TerritoryItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code /breach claim info | remove <id> <motivo> | give <cupboard|hammer> [giocatore] [quantità]} per lo staff. Gira sul
 * main thread (come ogni comando dei moduli): legge l'indice in memoria e tocca il mondo direttamente.
 */
public final class ClaimSubcommand implements AdminSubcommand {

    public static final String PERMISSION = "breach.admin.claim";
    private static final String USAGE =
            "/breach claim <info | remove <id> <motivo> | give <cupboard|hammer> [giocatore] [quantità]>";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final ClaimService claims;
    private final Supplier<TerritoryItems> items;
    private final Supplier<PlacedBlockTracker> placed;
    private final Executor main;

    /** Item e registro esistono solo dopo {@code onEnable}: si leggono quando servono. */
    public ClaimSubcommand(ClaimService claims, Supplier<TerritoryItems> items, Supplier<PlacedBlockTracker> placed,
                           Executor main) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.main = Objects.requireNonNull(main, "main");
        this.items = Objects.requireNonNull(items, "items");
        this.placed = Objects.requireNonNull(placed, "placed");
    }

    @Override
    public String name() {
        return "claim";
    }

    @Override
    public String usage() {
        return USAGE;
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        List<String> args = invocation.args();
        if (args.isEmpty()) {
            return done(CommandResult.error("Uso: " + USAGE));
        }
        return switch (args.getFirst().toLowerCase(Locale.ROOT)) {
            case "info" -> done(info(invocation));
            case "remove" -> remove(invocation);
            case "give" -> done(give(invocation));
            default -> done(CommandResult.error("Uso: " + USAGE));
        };
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        if (args.size() == 1) {
            return byPrefix(List.of("info", "remove", "give"), args.getFirst());
        }
        if (args.size() == 2 && args.getFirst().equalsIgnoreCase("give")) {
            return byPrefix(List.of("cupboard", "hammer"), args.get(1));
        }
        if (args.size() == 3 && args.getFirst().equalsIgnoreCase("give")) {
            return byPrefix(onlinePlayers, args.get(2));
        }
        return List.of();
    }

    private CommandResult info(CommandInvocation invocation) {
        Player player = invocation.isPlayer() ? Bukkit.getPlayer(invocation.senderId()) : null;
        if (player == null) {
            return CommandResult.error("Solo un giocatore può usare /breach claim info.");
        }
        Location at = player.getLocation();
        Optional<LiveClaim> found = claims.claimCovering(at.getWorld().getUID(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        if (found.isEmpty()) {
            return CommandResult.of(ChatLine.info("Qui non c'è nessun claim."));
        }
        LiveClaim claim = found.get();
        ClaimBox box = claim.box();
        String members = claim.members().stream().map(ClaimSubcommand::name).sorted().collect(Collectors.joining(", "));
        return CommandResult.of(
                ChatLine.info("Claim #" + claim.id() + (claim.pending() ? " (in creazione)" : "")),
                ChatLine.info("Creato da " + name(claim.createdBy()) + " il " + DATE.format(claim.createdAt())),
                ChatLine.info("Cupboard: " + box.x() + " " + box.y() + " " + box.z() + ", raggio " + box.radius()),
                ChatLine.info("Autorizzati (" + claim.members().size() + "): " + (members.isEmpty() ? "nessuno" : members)));
    }

    private CompletableFuture<CommandResult> remove(CommandInvocation invocation) {
        List<String> args = invocation.args();
        if (args.size() < 3) {
            return done(CommandResult.error("Uso: /breach claim remove <id> <motivo>"));
        }
        long id;
        try {
            id = Long.parseLong(args.get(1));
        } catch (NumberFormatException e) {
            return done(CommandResult.error("Id del claim non valido: " + args.get(1)));
        }
        Optional<LiveClaim> found = claims.claimById(id);
        if (found.isEmpty()) {
            return done(CommandResult.error("Nessun claim #" + id + "."));
        }
        ClaimBox box = found.get().box();
        String reason = String.join(" ", args.subList(2, args.size()));
        return claims.remove(id, invocation.senderId(), reason).handleAsync((ignored, error) -> {
            if (error != null) {
                return CommandResult.error("Impossibile rimuovere il claim #" + id + ": " + rootMessage(error));
            }
            boolean blockRemoved = removeCupboardBlock(box);
            return CommandResult.of(ChatLine.success("Claim #" + id + " rimosso"
                    + (blockRemoved ? " insieme al cupboard." : " (chunk non caricato: il blocco del cupboard resta).")));
        }, main);
    }

    private CommandResult give(CommandInvocation invocation) {
        List<String> args = invocation.args();
        if (args.size() < 2) {
            return CommandResult.error("Uso: /breach claim give <cupboard|hammer> [giocatore] [quantità]");
        }
        TerritoryItems territoryItems = items.get();
        if (territoryItems == null) {
            return CommandResult.error("Il modulo territory non è ancora attivo.");
        }
        String kind = args.get(1).toLowerCase(Locale.ROOT);
        if (!kind.equals("cupboard") && !kind.equals("hammer")) {
            return CommandResult.error("Item sconosciuto: " + args.get(1) + " (cupboard o hammer).");
        }
        Player target;
        if (args.size() >= 3) {
            target = Bukkit.getPlayerExact(args.get(2));
            if (target == null) {
                return CommandResult.error("Giocatore non online: " + args.get(2) + ".");
            }
        } else if (invocation.isPlayer()) {
            target = Bukkit.getPlayer(invocation.senderId());
        } else {
            return CommandResult.error("Dalla console indica un giocatore.");
        }
        int amount = 1;
        if (args.size() >= 4) {
            try {
                amount = Integer.parseInt(args.get(3));
            } catch (NumberFormatException e) {
                amount = 0;
            }
            if (amount < 1 || amount > 64) {
                return CommandResult.error("Quantità non valida: " + args.get(3) + " (da 1 a 64).");
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        if (kind.equals("cupboard")) {
            stacks.add(territoryItems.cupboard(amount));
        } else {
            for (int i = 0; i < amount; i++) {
                stacks.add(territoryItems.hammer());
            }
        }
        Player receiver = target;
        receiver.getInventory().addItem(stacks.toArray(ItemStack[]::new)).values()
                .forEach(left -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), left));
        return CommandResult.of(ChatLine.success("Dati " + amount + "× " + kind + " a " + receiver.getName() + "."));
    }

    /** Toglie il blocco del cupboard se il chunk è caricato; un chunk non caricato non si carica per questo. */
    private boolean removeCupboardBlock(ClaimBox box) {
        World world = Bukkit.getWorld(box.world());
        TerritoryItems territoryItems = items.get();
        if (world == null || territoryItems == null || !world.isChunkLoaded(box.x() >> 4, box.z() >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(box.x(), box.y(), box.z());
        if (block.getType() == territoryItems.cupboardMaterial()) {
            block.setType(Material.AIR);
        }
        PlacedBlockTracker tracker = placed.get();
        if (tracker != null) {
            tracker.unmark(block);
        }
        return true;
    }

    private static String name(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private static List<String> byPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static CompletableFuture<CommandResult> done(CommandResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
