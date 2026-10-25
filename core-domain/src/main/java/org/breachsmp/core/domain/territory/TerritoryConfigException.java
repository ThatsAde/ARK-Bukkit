package org.breachsmp.core.domain.territory;

import java.util.List;

/** {@code territory.yml} non valido: porta tutti gli errori trovati, così si correggono in un giro solo. */
public final class TerritoryConfigException extends RuntimeException {

    private final List<String> errors;

    public TerritoryConfigException(List<String> errors) {
        super("territory.yml non valido: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
