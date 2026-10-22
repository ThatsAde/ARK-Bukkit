package org.breachsmp.core.api.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Una riga di {@code audit_log} (spec §14).
 *
 * @param actor   chi ha agito; {@code null} = console o sistema
 * @param action  codice dell'azione (es. {@code ECO_GIVE}), massimo 48 caratteri
 * @param subject oggetto dell'azione (es. {@code player:<uuid>}), massimo 64 caratteri, può essere {@code null}
 * @param detail  dettagli serializzati in JSON (solo testo, interi, booleani)
 */
public record AuditEntry(UUID actor, String action, String subject, Map<String, ?> detail) {

    public AuditEntry {
        if (action == null || action.isBlank() || action.length() > 48) {
            throw new IllegalArgumentException("Azione di audit non valida: '" + action + "' (1-48 caratteri)");
        }
        if (subject != null && subject.length() > 64) {
            throw new IllegalArgumentException("Soggetto di audit troppo lungo (massimo 64 caratteri): " + subject);
        }
        detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }
}
