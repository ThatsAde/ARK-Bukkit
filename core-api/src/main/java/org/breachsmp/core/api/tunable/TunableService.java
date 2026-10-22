package org.breachsmp.core.api.tunable;

/**
 * Tunable nel DB, modificabili con {@code /breach tunable} (D-17: solo interi in M1).
 * Registra in {@code onLoad}: la piattaforma carica i valori dal DB prima di {@code enableAll()}.
 */
public interface TunableService {

    /**
     * @param key formato {@code [a-z][a-z0-9_.]{0,63}}, es. {@code economy.transfer_fee_bp}
     * @throws IllegalArgumentException chiave non valida o default fuori da [min, max]
     * @throws IllegalStateException    chiave già registrata
     */
    Tunable registerLong(String key, long defaultValue, long min, long max, String description);
}
