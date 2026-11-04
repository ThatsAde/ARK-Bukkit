package org.breachsmp.core.territory.claim;

import org.breachsmp.core.api.territory.ClaimView;
import org.breachsmp.core.domain.territory.ClaimBox;
import org.breachsmp.core.domain.territory.PinHash;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stato in memoria di un claim; si modifica solo sul main thread, sempre dopo la scrittura nel DB (D-36). Uguaglianza per
 * identità: è la chiave dell'indice, e un claim riservato non ha ancora un id.
 */
public final class LiveClaim {

    private volatile long id;
    private final ClaimBox box;
    private final UUID createdBy;
    private final Instant createdAt;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private volatile PinHash pin;
    private volatile boolean pending;

    LiveClaim(long id, ClaimBox box, UUID createdBy, Instant createdAt, Set<UUID> members, PinHash pin, boolean pending) {
        this.id = id;
        this.box = Objects.requireNonNull(box, "box");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.members.addAll(members);
        this.pin = pin;
        this.pending = pending;
    }

    /** Id nel DB; {@code 0} finché il claim è solo riservato. */
    public long id() {
        return id;
    }

    public ClaimBox box() {
        return box;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Vista non modificabile degli autorizzati. */
    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public Optional<PinHash> pin() {
        return Optional.ofNullable(pin);
    }

    /** {@code true} finché l'INSERT non è confermato: il claim occupa già l'area ma non ha un id (D-36). */
    public boolean pending() {
        return pending;
    }

    public ClaimView view() {
        return new ClaimView(id, box.world(), box.x(), box.y(), box.z(), box.radius());
    }

    void confirm(long id) {
        this.id = id;
        this.pending = false;
    }

    void addMember(UUID uuid) {
        members.add(uuid);
    }

    void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    void pin(PinHash pin) {
        this.pin = pin;
    }

    @Override
    public String toString() {
        return "LiveClaim[id=" + id + ", box=" + box + ", pending=" + pending + "]";
    }
}
