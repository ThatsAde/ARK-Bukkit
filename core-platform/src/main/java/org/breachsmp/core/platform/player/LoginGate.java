package org.breachsmp.core.platform.player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decide se un giocatore può entrare (spec §3): salva il profilo, poi chiama gli hook dei moduli.
 * Qualsiasi errore lo rifiuta: un'economia con saldi non caricati è peggio di un'attesa (fail-closed).
 */
public final class LoginGate {

    static final String KICK_MESSAGE = "BreachCore non riesce a caricare il tuo profilo. Riprova tra qualche istante.";

    private final BiConsumer<UUID, String> upsert;
    private final PlayerHookRegistry hooks;
    private final Logger logger;

    public LoginGate(BiConsumer<UUID, String> upsert, PlayerHookRegistry hooks, Logger logger) {
        this.upsert = Objects.requireNonNull(upsert, "upsert");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Thread asincrono di login, bloccante. @return il messaggio di espulsione, oppure vuoto se il giocatore può entrare */
    public Optional<String> admit(UUID uuid, String name) {
        try {
            upsert.accept(uuid, name);
            hooks.runLogin(uuid, name);
            return Optional.empty();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Accesso rifiutato a " + name + ": " + e.getMessage(), e);
            return Optional.of(KICK_MESSAGE);
        }
    }
}
