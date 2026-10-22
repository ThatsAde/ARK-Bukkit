package org.breachsmp.core.platform.player;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Collega login e uscita di Paper a {@link LoginGate} e agli hook dei moduli. */
public final class PlayerListener implements Listener {

    private final LoginGate gate;
    private final PlayerHookRegistry hooks;

    public PlayerListener(LoginGate gate, PlayerHookRegistry hooks) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return; // già rifiutato da ban/whitelist: niente I/O inutile
        }
        gate.admit(event.getUniqueId(), event.getName()).ifPresent(message ->
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(message)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hooks.runQuit(event.getPlayer().getUniqueId());
    }
}
