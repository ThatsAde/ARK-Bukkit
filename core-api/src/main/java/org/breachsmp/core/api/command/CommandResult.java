package org.breachsmp.core.api.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Esito di un comando: righe per chi l'ha eseguito e avvisi per altri giocatori. */
public record CommandResult(List<ChatLine> lines, List<Notice> notices) {

    public CommandResult {
        lines = List.copyOf(lines);
        notices = List.copyOf(notices);
    }

    public static CommandResult of(ChatLine... lines) {
        return new CommandResult(List.of(lines), List.of());
    }

    public static CommandResult error(String text) {
        return of(ChatLine.error(text));
    }

    public CommandResult withNotice(UUID player, ChatLine line) {
        List<Notice> all = new ArrayList<>(notices);
        all.add(new Notice(player, line));
        return new CommandResult(lines, all);
    }
}
