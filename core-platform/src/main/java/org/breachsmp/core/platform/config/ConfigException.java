package org.breachsmp.core.platform.config;

import java.util.List;

/** Configurazione non valida. Contiene tutti gli errori trovati, non solo il primo. */
public final class ConfigException extends RuntimeException {

    private final List<String> errors;

    public ConfigException(List<String> errors) {
        super("Configurazione non valida:\n - " + String.join("\n - ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
