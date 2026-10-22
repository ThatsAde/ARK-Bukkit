package org.breachsmp.core.platform.tunable;

/** Esito di una modifica: stato nuovo e valore precedente. */
public record TunableChange(TunableView now, long previous) {
}
