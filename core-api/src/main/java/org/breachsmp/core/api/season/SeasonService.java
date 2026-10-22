package org.breachsmp.core.api.season;

/** Stagione corrente (spec §12); letta all'avvio, il cambio di stagione richiede un riavvio (M9). */
public interface SeasonService {

    int currentSeasonId();
}
