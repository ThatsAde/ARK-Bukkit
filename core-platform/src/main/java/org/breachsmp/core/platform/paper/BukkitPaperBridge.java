package org.breachsmp.core.platform.paper;

import org.breachsmp.core.paper.PaperBridge;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Objects;

/** {@link PaperBridge} sul plugin BreachCore. */
public final class BukkitPaperBridge implements PaperBridge {

    private final JavaPlugin plugin;

    public BukkitPaperBridge(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(Objects.requireNonNull(listener, "listener"), plugin);
    }

    @Override
    public Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }
}
