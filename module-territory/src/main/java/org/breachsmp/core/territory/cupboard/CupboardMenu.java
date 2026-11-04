package org.breachsmp.core.territory.cupboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.claim.LiveClaim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menu del Tool Cupboard per gli autorizzati (design M2, T5): autorizzati (clic = togli, conferma col secondo clic), cambio
 * PIN, confine, informazioni. I clic sono sempre annullati: le icone non si prelevano.
 */
public final class CupboardMenu implements Listener {

    private static final int SIZE = 27;
    private static final int MEMBER_SLOTS = 18;
    private static final int INFO_SLOT = 20;
    private static final int PIN_SLOT = 22;
    private static final int BORDER_SLOT = 24;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    /** Menu aperto: chi è il claim, quale testa è in quale slot, quale rimozione aspetta conferma. */
    private static final class Holder implements InventoryHolder {
        private final LiveClaim claim;
        private final UUID[] members = new UUID[MEMBER_SLOTS];
        private Inventory inventory;
        private UUID confirming;

        private Holder(LiveClaim claim) {
            this.claim = claim;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ClaimService claims;
    private final PinDialogs pins;
    private final ClaimVisualizer visualizer;
    private final CoreExecutors executors;
    private final Logger logger;

    public CupboardMenu(ClaimService claims, PinDialogs pins, ClaimVisualizer visualizer, CoreExecutors executors,
                        Logger logger) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.pins = Objects.requireNonNull(pins, "pins");
        this.visualizer = Objects.requireNonNull(visualizer, "visualizer");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player, LiveClaim claim) {
        Holder holder = new Holder(claim);
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("Tool Cupboard"));
        render(holder);
        player.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) {
            return;
        }
        if (!claims.isAuthorized(holder.claim.id(), player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        if (slot < MEMBER_SLOTS && holder.members[slot] != null) {
            clickMember(player, holder, holder.members[slot]);
        } else if (slot == PIN_SLOT) {
            player.closeInventory();
            pins.askNewPin(player, holder.claim);
        } else if (slot == BORDER_SLOT) {
            player.closeInventory();
            visualizer.show(player, holder.claim.box());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void clickMember(Player player, Holder holder, UUID target) {
        if (!target.equals(holder.confirming)) {
            holder.confirming = target;
            render(holder);
            return;
        }
        holder.confirming = null;
        claims.unauthorize(holder.claim.id(), target, player.getUniqueId()).whenCompleteAsync((ignored, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Impossibile togliere un autorizzato dal claim #" + holder.claim.id(), error);
                player.sendMessage(Component.text("Impossibile togliere l'autorizzazione, riprova.", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Autorizzazione tolta.", NamedTextColor.GREEN));
            if (target.equals(player.getUniqueId())) {
                player.closeInventory();
            } else if (player.getOpenInventory().getTopInventory() == holder.inventory) {
                render(holder);
            }
        }, executors.main());
    }

    private void render(Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        Arrays.fill(holder.members, null);
        List<UUID> members = new ArrayList<>(holder.claim.members());
        for (int slot = 0; slot < MEMBER_SLOTS && slot < members.size(); slot++) {
            UUID member = members.get(slot);
            holder.members[slot] = member;
            inventory.setItem(slot, head(member, member.equals(holder.confirming)));
        }
        LiveClaim claim = holder.claim;
        inventory.setItem(INFO_SLOT, icon(Material.BOOK, "Informazioni", List.of(
                "Creata da: " + name(claim.createdBy()),
                "Il: " + DATE.format(claim.createdAt()),
                "Raggio: " + claim.box().radius() + " blocchi",
                "Autorizzati: " + members.size())));
        inventory.setItem(PIN_SLOT, icon(Material.TRIPWIRE_HOOK, "Cambia PIN", List.of("Imposta un nuovo PIN.")));
        inventory.setItem(BORDER_SLOT, icon(Material.ENDER_EYE, "Mostra confine", List.of("Solo tu lo vedi, per 10 secondi.")));
    }

    private static ItemStack head(UUID member, boolean confirming) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(member);
        ItemStack head = icon(Material.PLAYER_HEAD, name(member), List.of(confirming
                ? "Clicca di nuovo per confermare."
                : "Clicca per togliere l'autorizzazione."));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        return head;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        });
        return stack;
    }

    private static String name(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }
}
