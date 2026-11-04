package org.breachsmp.core.territory;

import org.breachsmp.core.api.tunable.Tunable;
import org.breachsmp.core.api.tunable.TunableService;

/** Tunable del territorio (D-38). Il raggio vale per i claim nuovi: quello di un claim è salvato alla creazione. */
public record TerritoryTunables(Tunable claimRadius, Tunable claimGap, Tunable maxCupboardsPerPlayer,
                                Tunable pinAttemptsPer10Min) {

    public static TerritoryTunables register(TunableService tunables) {
        return new TerritoryTunables(
                tunables.registerLong("territory.claim_radius", 16, 1, 64,
                        "Raggio dei claim nuovi in blocchi (cubo di lato 2*raggio+1 attorno al Tool Cupboard)"),
                tunables.registerLong("territory.claim_gap", 0, 0, 64,
                        "Blocchi minimi tra il claim nuovo e un claim dove il giocatore non è autorizzato"),
                tunables.registerLong("territory.max_cupboards_per_player", 50, 1, 1_000,
                        "Tool Cupboard che un giocatore può aver piazzato nella stagione"),
                tunables.registerLong("territory.pin_attempts_per_10_min", 5, 1, 100,
                        "Tentativi di PIN in 10 minuti, per giocatore e per claim"));
    }
}
