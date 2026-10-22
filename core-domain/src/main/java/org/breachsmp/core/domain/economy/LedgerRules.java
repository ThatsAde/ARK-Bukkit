package org.breachsmp.core.domain.economy;

import org.breachsmp.core.api.economy.AccountFrozenException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.InsufficientFundsException;
import org.breachsmp.core.api.economy.InvalidTransactionException;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.util.JsonText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Regole pure del ledger (spec §5), usate dal ledger JDBC e dai test di proprietà: niente I/O. */
public final class LedgerRules {

    /** {@code ledger_entry.seq} è TINYINT UNSIGNED. */
    public static final int MAX_LEGS = 255;

    /** Stato di un conto bloccato con {@code FOR UPDATE}. */
    public record AccountState(long id, AccountRef ref, long balance, boolean frozen) {
    }

    /** Una gamba applicata a un conto bloccato. */
    public record Posting(AccountState account, long delta) {
    }

    private LedgerRules() {
    }

    /** Controlli che non richiedono il DB. @throws InvalidTransactionException se la richiesta è malformata */
    public static void validate(TxRequest request) {
        if (!TxKinds.KNOWN.contains(request.kind())) {
            throw new InvalidTransactionException("Tipo di transazione sconosciuto: " + request.kind());
        }
        List<Leg> legs = request.legs();
        if (legs.size() < 2 || legs.size() > MAX_LEGS) {
            throw new InvalidTransactionException("Una transazione deve avere tra 2 e " + MAX_LEGS + " gambe, ne ha " + legs.size());
        }
        Set<AccountRef> seen = new HashSet<>();
        long sum = 0;
        for (Leg leg : legs) {
            if (leg.delta() == 0) {
                throw new InvalidTransactionException("Gamba a importo zero sul conto " + leg.account());
            }
            if (!seen.add(leg.account())) {
                throw new InvalidTransactionException("Conto ripetuto nella stessa transazione: " + leg.account());
            }
            try {
                sum = Math.addExact(sum, leg.delta());
            } catch (ArithmeticException e) {
                throw new InvalidTransactionException("Importi fuori scala");
            }
        }
        if (sum != 0) {
            throw new InvalidTransactionException("La somma delle gambe deve essere 0 (partita doppia), è " + sum);
        }
        try {
            JsonText.write(request.meta());
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionException("Meta non valida: " + e.getMessage());
        }
    }

    /**
     * Saldi dopo la transazione, nello stesso ordine di {@code postings}.
     *
     * @param bypassFreeze {@code true} per i tipi admin e di sistema ({@link TxKinds#bypassesFreeze})
     * @throws AccountFrozenException     se un conto è congelato e il tipo non lo può toccare
     * @throws InsufficientFundsException se un conto che non può andare in negativo finirebbe sotto zero
     */
    public static List<Long> balancesAfter(List<Posting> postings, boolean bypassFreeze) {
        if (!bypassFreeze) {
            for (Posting posting : postings) {
                if (posting.account().frozen()) {
                    throw new AccountFrozenException(posting.account().ref());
                }
            }
        }
        List<Long> balances = new ArrayList<>(postings.size());
        for (Posting posting : postings) {
            long after;
            try {
                after = Math.addExact(posting.account().balance(), posting.delta());
            } catch (ArithmeticException e) {
                throw new InvalidTransactionException("Saldo fuori scala sul conto " + posting.account().ref());
            }
            if (after < 0 && !posting.account().ref().mayGoNegative()) {
                throw new InsufficientFundsException(posting.account().ref());
            }
            balances.add(after);
        }
        return balances;
    }
}
