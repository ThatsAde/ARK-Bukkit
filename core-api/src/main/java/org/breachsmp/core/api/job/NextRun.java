package org.breachsmp.core.api.job;

import java.time.LocalDateTime;

/** Calcola la prossima esecuzione, strettamente dopo {@code now} (ora locale del server). */
@FunctionalInterface
public interface NextRun {

    LocalDateTime after(LocalDateTime now);
}
