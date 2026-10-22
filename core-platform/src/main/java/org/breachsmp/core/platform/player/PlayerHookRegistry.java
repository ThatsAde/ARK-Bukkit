package org.breachsmp.core.platform.player;

import org.breachsmp.core.api.player.LoginHook;
import org.breachsmp.core.api.player.PlayerHooks;
import org.breachsmp.core.api.player.QuitHook;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Hook di login e uscita dei moduli, chiamati in ordine di registrazione. */
public final class PlayerHookRegistry implements PlayerHooks {

    private record Registration<H>(String moduleId, H hook) {
    }

    private final Logger logger;
    private final List<Registration<LoginHook>> loginHooks = new CopyOnWriteArrayList<>();
    private final List<Registration<QuitHook>> quitHooks = new CopyOnWriteArrayList<>();

    public PlayerHookRegistry(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onLogin(String moduleId, LoginHook hook) {
        loginHooks.add(new Registration<>(Objects.requireNonNull(moduleId, "moduleId"), Objects.requireNonNull(hook, "hook")));
    }

    @Override
    public void onQuit(String moduleId, QuitHook hook) {
        quitHooks.add(new Registration<>(Objects.requireNonNull(moduleId, "moduleId"), Objects.requireNonNull(hook, "hook")));
    }

    /** Thread asincrono di login, bloccante. Il primo errore interrompe la catena e rifiuta il giocatore. */
    public void runLogin(UUID uuid, String name) {
        for (Registration<LoginHook> registration : loginHooks) {
            try {
                registration.hook().onLogin(uuid, name);
            } catch (Exception e) {
                throw new LoginRejectedException("Hook di login del modulo '" + registration.moduleId() + "' fallito: "
                        + e.getMessage(), e);
            }
        }
    }

    /** Main thread. Un hook che fallisce viene registrato e non ferma gli altri. */
    public void runQuit(UUID uuid) {
        for (Registration<QuitHook> registration : quitHooks) {
            try {
                registration.hook().onQuit(uuid);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Hook di uscita del modulo '" + registration.moduleId() + "' fallito", e);
            }
        }
    }
}
