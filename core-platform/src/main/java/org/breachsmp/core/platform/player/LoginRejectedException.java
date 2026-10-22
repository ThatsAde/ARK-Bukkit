package org.breachsmp.core.platform.player;

/** Un hook di login di un modulo ha fallito: il giocatore non entra (fail-closed, spec §3). */
public final class LoginRejectedException extends RuntimeException {

    public LoginRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
