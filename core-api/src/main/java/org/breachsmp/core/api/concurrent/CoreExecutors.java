package org.breachsmp.core.api.concurrent;

import java.util.concurrent.Executor;

/**
 * Gli executor della piattaforma (spec §3).
 * Bukkit API solo su {@link #main()}, I/O solo su {@link #db()},
 * calcolo puro su {@link #compute()}, matching delle azioni solo su {@link #market()}.
 */
public interface CoreExecutors {

    /** Main thread del server. */
    Executor main();

    /** Query e transazioni; dimensione pari al pool del DB. */
    Executor db();

    /** Calcoli puri (bracket, statistiche); piccolo e fisso. */
    Executor compute();

    /** Un solo thread (single-writer) per il matching engine. */
    Executor market();
}
