package org.breachsmp.core.api.job;

/**
 * Job pianificati con lease su {@code scheduled_job} (spec §9): con più processi un job gira una volta sola.
 * Registra in {@code onLoad} o {@code onEnable}; dopo l'avvio dello scheduler è un errore ({@link IllegalStateException}).
 */
public interface JobScheduler {

    void register(String jobId, NextRun nextRun, Job job);
}
