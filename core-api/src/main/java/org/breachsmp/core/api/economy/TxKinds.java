package org.breachsmp.core.api.economy;

import java.util.Set;

/** Tipi di transazione noti al ledger: un tipo sconosciuto viene rifiutato (spec §5). */
public final class TxKinds {

    public static final String PAY = "PAY";
    public static final String ADMIN_GIVE = "ADMIN_GIVE";
    public static final String ADMIN_TAKE = "ADMIN_TAKE";
    public static final String ROLLBACK = "ROLLBACK";
    public static final String STARTING_BALANCE = "STARTING_BALANCE";

    public static final Set<String> KNOWN = Set.of(PAY, ADMIN_GIVE, ADMIN_TAKE, ROLLBACK, STARTING_BALANCE);

    private static final Set<String> FREEZE_BYPASS = Set.of(ADMIN_GIVE, ADMIN_TAKE, ROLLBACK, STARTING_BALANCE);

    private TxKinds() {
    }

    /** I tipi admin e di sistema toccano anche i conti congelati: sono la via di riparazione (E13). */
    public static boolean bypassesFreeze(String kind) {
        return FREEZE_BYPASS.contains(kind);
    }
}
