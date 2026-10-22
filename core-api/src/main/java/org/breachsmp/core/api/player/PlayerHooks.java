package org.breachsmp.core.api.player;

/**
 * Registro degli hook di login e uscita dei moduli.
 * Registra in {@code onLoad} o {@code onEnable}; la piattaforma attiva tutto dopo {@code enableAll()}.
 */
public interface PlayerHooks {

    void onLogin(String moduleId, LoginHook hook);

    void onQuit(String moduleId, QuitHook hook);
}
