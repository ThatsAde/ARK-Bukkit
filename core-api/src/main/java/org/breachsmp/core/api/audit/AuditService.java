package org.breachsmp.core.api.audit;

import java.util.concurrent.CompletableFuture;

/** Audit append-only (spec §14, D-18). */
public interface AuditService {

    /** Scrive sul thread DB. */
    CompletableFuture<Void> record(AuditEntry entry);

    /** Scrive subito, bloccando: solo da thread che possono fare I/O (DB, login). Mai dal main thread. */
    void recordNow(AuditEntry entry);
}
