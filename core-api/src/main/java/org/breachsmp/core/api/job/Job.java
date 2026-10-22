package org.breachsmp.core.api.job;

/** Lavoro pianificato: gira sul thread DB con il lease preso, I/O bloccante permesso. */
@FunctionalInterface
public interface Job {

    void run() throws Exception;
}
