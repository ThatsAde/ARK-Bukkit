package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.economy.AccountFrozenException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.InvalidTransactionException;
import org.breachsmp.core.economy.admin.RollbackRefusedException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Testi per l'utente degli errori del ledger. */
final class EconomyMessages {

    static final String UNKNOWN_OUTCOME =
            "Esito incerto per un errore del server: controlla il saldo con /balance prima di riprovare.";

    private EconomyMessages() {
    }

    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * @param self il conto di chi agisce (o del giocatore bersaglio per lo staff); può essere {@code null}.
     *             Un errore generico può essere arrivato durante il commit: il testo non promette "nessun addebito".
     */
    static String failure(Throwable error, AccountRef self) {
        return switch (unwrap(error)) {
            case InsufficientFundsException e when e.account().equals(self) -> "Saldo insufficiente.";
            case InsufficientFundsException e -> "Fondi insufficienti sul conto " + e.account() + ".";
            case AccountFrozenException e when e.account().equals(self) -> "Il tuo conto è congelato: i trasferimenti sono bloccati.";
            case AccountFrozenException e -> "Il conto di destinazione è congelato.";
            case RollbackRefusedException e -> e.getMessage();
            case InvalidTransactionException e -> e.getMessage();
            case IllegalArgumentException e -> e.getMessage();
            default -> UNKNOWN_OUTCOME;
        };
    }
}
