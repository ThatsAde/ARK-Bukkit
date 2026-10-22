package org.breachsmp.core.economy.admin;

import org.breachsmp.core.api.economy.LedgerException;

/** Rollback non ammesso (transazione assente, di un'altra stagione o già un rollback, D-25): nessun movimento. */
public final class RollbackRefusedException extends LedgerException {

    public RollbackRefusedException(String message) {
        super(message);
    }
}
