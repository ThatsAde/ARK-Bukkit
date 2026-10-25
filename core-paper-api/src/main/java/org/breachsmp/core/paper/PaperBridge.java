package org.breachsmp.core.paper;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;

/**
 * Accesso a Paper per i moduli che non possono farne a meno (listener, chiavi PDC, ricette, file di config) – D-34.
 * Lo fornisce la piattaforma nel {@code ServiceRegistry}; i moduli non dipendono mai da {@code core-platform}.
 */
public interface PaperBridge {

    /** Il plugin BreachCore: per {@code NamespacedKey}, scheduler e ricette. */
    Plugin plugin();

    /** Registra un listener Bukkit a nome di BreachCore. Da chiamare in {@code onEnable}. */
    void registerListener(Listener listener);

    /** Cartella del plugin ({@code plugins/BreachCore}). */
    Path dataFolder();
}
