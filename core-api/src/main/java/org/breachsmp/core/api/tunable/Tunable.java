package org.breachsmp.core.api.tunable;

/** Valore operativo modificabile a caldo (spec §3). {@link #get()} è una lettura in memoria, sicura da qualsiasi thread. */
public interface Tunable {

    String key();

    long get();
}
