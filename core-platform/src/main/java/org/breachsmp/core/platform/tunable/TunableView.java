package org.breachsmp.core.platform.tunable;

/** Fotografia di un tunable per {@code /breach tunable}. */
public record TunableView(String key, long value, long defaultValue, long min, long max, String description) {
}
