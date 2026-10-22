package org.breachsmp.core.domain.economy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Importi in unità minime (E1: 1 moneta = 100 unità) e loro forma testuale (E21). Solo UI: il ledger lavora sempre in {@code long}. */
public final class Money {

    public static final long UNITS_PER_COIN = 100;

    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,15})(?:[.,](\\d{1,2}))?");

    private Money() {
    }

    /**
     * Legge un importo scritto dall'utente: cifre con al massimo 2 decimali, separatore {@code ,} o {@code .}, niente
     * separatori delle migliaia. {@code "12.5"} = 12,50 monete.
     *
     * @throws IllegalArgumentException con un messaggio per l'utente se il testo non è valido o l'importo non è positivo
     */
    public static long parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Importo mancante");
        }
        String trimmed = text.trim();
        Matcher matcher = AMOUNT.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Importo non valido: '" + trimmed
                    + "'. Usa un numero con al massimo 2 decimali, es. 12,50");
        }
        long whole = Long.parseLong(matcher.group(1));
        String fraction = matcher.group(2);
        long cents = fraction == null ? 0 : Long.parseLong(fraction.length() == 1 ? fraction + "0" : fraction);
        long units = whole * UNITS_PER_COIN + cents;
        if (units <= 0) {
            throw new IllegalArgumentException("L'importo deve essere maggiore di zero");
        }
        return units;
    }

    /** {@code 123456} → {@code "1.234,56 monete"}; decimali solo se diversi da zero; {@code "1 moneta"} al singolare. */
    public static String format(long units) {
        long whole = Math.abs(units / UNITS_PER_COIN);
        long cents = Math.abs(units % UNITS_PER_COIN);
        StringBuilder out = new StringBuilder();
        if (units < 0) {
            out.append('-');
        }
        out.append(groupThousands(whole));
        if (cents != 0) {
            out.append(',').append(cents < 10 ? "0" : "").append(cents);
        }
        out.append(Math.abs(units) == UNITS_PER_COIN ? " moneta" : " monete");
        return out.toString();
    }

    private static String groupThousands(long value) {
        String digits = Long.toString(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append('.');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
