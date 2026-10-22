package org.breachsmp.core.api.player;

import java.util.UUID;

/**
 * Chiamato sul thread asincrono di login, dopo l'upsert del profilo: I/O bloccante permesso.
 * Un'eccezione rifiuta il giocatore (fail-closed, spec §3).
 */
@FunctionalInterface
public interface LoginHook {

    void onLogin(UUID uuid, String name) throws Exception;
}
