package org.breachsmp.core.api.module;

import java.util.Collection;
import java.util.List;

/**
 * Un modulo di dominio di BreachCore (nella spec: {@code BreachModule}).
 *
 * <p>Ciclo di vita gestito dalla piattaforma, in ordine di dipendenza:
 * {@link #onLoad} → {@link #onEnable} → … → {@link #onDisable} (ordine inverso).
 * Un'eccezione in {@code onLoad} o {@code onEnable} blocca l'avvio dell'intero plugin (fail-closed).
 */
public interface ARKModule {

    /** Id stabile e minuscolo ({@code economy}, {@code territory}); è anche la chiave {@code modules.<id>.enabled}. */
    String id();

    /** Registra servizi e prepara lo stato. Niente job né listener qui. */
    void onLoad(ModuleContext context);

    /** Avvia job e listener. Tutte le dipendenze sono già abilitate. */
    void onEnable();

    /**
     * Svuota le code e fa flush. Chiamato in ordine inverso di dipendenza.
     * Non usare {@link ModuleContext#sync} né {@code executors().main()} qui o nel lavoro che ne deriva:
     * durante lo spegnimento lo scheduler Bukkit rifiuta nuovi task e il future fallirebbe.
     */
    void onDisable();

    /** Stato per {@code /breach health}. Può essere chiamato da qualsiasi thread: niente Bukkit API, niente I/O bloccante. */
    HealthStatus health();

    /** Moduli che devono essere caricati e abilitati prima di questo. */
    default Collection<ModuleDependency> dependencies() {
        return List.of();
    }
}
