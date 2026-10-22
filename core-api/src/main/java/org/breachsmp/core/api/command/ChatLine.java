package org.breachsmp.core.api.command;

import java.util.Objects;

/** Una riga di testo per la chat, senza tipi Bukkit (D-15). */
public record ChatLine(Tone tone, String text) {

    public ChatLine {
        Objects.requireNonNull(tone, "tone");
        Objects.requireNonNull(text, "text");
    }

    public static ChatLine info(String text) {
        return new ChatLine(Tone.INFO, text);
    }

    public static ChatLine success(String text) {
        return new ChatLine(Tone.SUCCESS, text);
    }

    public static ChatLine warning(String text) {
        return new ChatLine(Tone.WARNING, text);
    }

    public static ChatLine error(String text) {
        return new ChatLine(Tone.ERROR, text);
    }

    public static ChatLine muted(String text) {
        return new ChatLine(Tone.MUTED, text);
    }
}
