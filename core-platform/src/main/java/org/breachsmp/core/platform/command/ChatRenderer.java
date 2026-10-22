package org.breachsmp.core.platform.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.Tone;

/** Traduce i tipi neutri di {@code core-api} in componenti Adventure. */
public final class ChatRenderer {

    private ChatRenderer() {
    }

    public static Component render(ChatLine line) {
        return Component.text(line.text(), color(line.tone()));
    }

    static NamedTextColor color(Tone tone) {
        return switch (tone) {
            case INFO -> NamedTextColor.WHITE;
            case SUCCESS -> NamedTextColor.GREEN;
            case WARNING -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
            case MUTED -> NamedTextColor.GRAY;
        };
    }
}
