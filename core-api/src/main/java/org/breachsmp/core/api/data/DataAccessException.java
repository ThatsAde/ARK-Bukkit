package org.breachsmp.core.api.data;

/** Errore di accesso al database: messaggio in italiano, causa SQL preservata. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
