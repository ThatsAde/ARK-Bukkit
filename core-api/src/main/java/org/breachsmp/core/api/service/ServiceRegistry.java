package org.breachsmp.core.api.service;

import java.util.Optional;

/** Registry tipizzato con cui i moduli pubblicano e trovano servizi senza conoscersi (invariante I7). */
public interface ServiceRegistry {

    /** Pubblica un servizio. Registrare due volte lo stesso tipo è un errore ({@link IllegalStateException}). */
    <T> void register(Class<T> type, T implementation);

    /** Servizio opzionale. */
    <T> Optional<T> find(Class<T> type);

    /** Servizio obbligatorio: se manca lancia {@link IllegalStateException} con un messaggio esplicito. */
    <T> T require(Class<T> type);
}
