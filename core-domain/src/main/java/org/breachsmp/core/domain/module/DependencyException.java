package org.breachsmp.core.domain.module;

/** Grafo dei moduli non valido (dipendenza mancante o ciclo): l'avvio fallisce. */
public final class DependencyException extends RuntimeException {

    public DependencyException(String message) {
        super(message);
    }
}
