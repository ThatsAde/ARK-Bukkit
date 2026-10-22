package org.breachsmp.core.economy.command;

import org.breachsmp.core.domain.economy.Money;
import org.breachsmp.core.economy.admin.EconomyAdmin;

import java.util.List;
import java.util.Locale;

/** Parsing puro degli argomenti dei sottocomandi admin: {@link IllegalArgumentException} con il testo per lo staff. */
final class AdminArgs {

    static final String ECO_USAGE = "Uso: /breach eco <give|take> <giocatore> <importo> <motivo>";
    static final String ROLLBACK_USAGE = "Uso: /breach rollback tx <id> <motivo>";
    static final String TARGET_USAGE = "Uso: /breach freeze|unfreeze <giocatore> <motivo>";

    record Eco(boolean give, String player, long amount, String reason) {
    }

    record Rollback(long txId, String reason) {
    }

    record Target(String player, String reason) {
    }

    private AdminArgs() {
    }

    static Eco eco(List<String> args) {
        if (args.size() < 4) {
            throw new IllegalArgumentException(ECO_USAGE);
        }
        String operation = args.getFirst().toLowerCase(Locale.ROOT);
        if (!operation.equals("give") && !operation.equals("take")) {
            throw new IllegalArgumentException(ECO_USAGE);
        }
        return new Eco(operation.equals("give"), args.get(1), Money.parse(args.get(2)), reason(args, 3));
    }

    static Rollback rollback(List<String> args) {
        if (args.size() < 3 || !args.getFirst().equalsIgnoreCase("tx")) {
            throw new IllegalArgumentException(ROLLBACK_USAGE);
        }
        long txId;
        try {
            txId = Long.parseLong(args.get(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Id transazione non valido: " + args.get(1));
        }
        return new Rollback(txId, reason(args, 2));
    }

    static Target target(List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException(TARGET_USAGE);
        }
        return new Target(args.getFirst(), reason(args, 1));
    }

    /** Secondo argomento opzionale di {@code /breach tx <giocatore> [pagina]}; default 1. */
    static int page(List<String> args) {
        if (args.size() < 2) {
            return 1;
        }
        try {
            int page = Integer.parseInt(args.get(1));
            if (page >= 1) {
                return page;
            }
        } catch (NumberFormatException e) {
            // gestito sotto
        }
        throw new IllegalArgumentException("Pagina non valida: " + args.get(1));
    }

    /** Argomenti da {@code from} in poi, uniti da spazi: obbligatorio, al massimo 200 caratteri. */
    static String reason(List<String> args, int from) {
        String reason = from >= args.size() ? "" : String.join(" ", args.subList(from, args.size())).trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Specifica un motivo.");
        }
        if (reason.length() > EconomyAdmin.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Motivo troppo lungo (massimo " + EconomyAdmin.MAX_REASON_LENGTH + " caratteri).");
        }
        return reason;
    }
}
