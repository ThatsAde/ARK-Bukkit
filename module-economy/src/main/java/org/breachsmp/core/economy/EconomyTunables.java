package org.breachsmp.core.economy;

import org.breachsmp.core.api.tunable.Tunable;
import org.breachsmp.core.api.tunable.TunableService;

/** Tunable dell'economia (D-32); gli importi sono in unità minime (100 = 1 moneta). */
public record EconomyTunables(Tunable startingBalance, Tunable transferFeeBp, Tunable payRatePerMinute, Tunable reconHour) {

    public static EconomyTunables register(TunableService tunables) {
        return new EconomyTunables(
                tunables.registerLong("economy.starting_balance", 0, 0, 100_000_000,
                        "Saldo iniziale alla prima entrata nella stagione, in unità minime (100 = 1 moneta)"),
                tunables.registerLong("economy.transfer_fee_bp", 0, 0, 5_000,
                        "Tassa su /pay in punti base (100 = 1%), va a SYS_TAX_POOL"),
                tunables.registerLong("economy.pay_rate_per_minute", 10, 1, 1_000,
                        "Pagamenti /pay al minuto per giocatore"),
                tunables.registerLong("economy.recon_hour", 4, 0, 23,
                        "Ora del server della riconciliazione notturna"));
    }
}
