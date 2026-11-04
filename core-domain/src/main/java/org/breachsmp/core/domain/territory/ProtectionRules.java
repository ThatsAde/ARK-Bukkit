package org.breachsmp.core.domain.territory;

/**
 * Regole di protezione dei claim (design M2, T7, T8, T23): il claim protegge solo ciò che è costruito. Decisioni pure:
 * i listener raccolgono il contesto e chiedono qui.
 */
public final class ProtectionRules {

    /** Nessun claim copre la posizione. */
    public static final long NO_CLAIM = 0L;

    /**
     * Contesto di un blocco.
     *
     * @param claimId         claim che copre il blocco ({@link #NO_CLAIM} = nessuno)
     * @param actorAuthorized chi agisce è autorizzato su quel claim (o ha il bypass dello staff)
     * @param structure       il blocco è stato piazzato da un giocatore (D-39); altrimenti è terreno
     */
    public record Spot(long claimId, boolean actorAuthorized, boolean structure) {
    }

    public enum Action { PLACE, BREAK, OPEN_DOOR, USE_REDSTONE, HAMMER, OPEN_CONTAINER }

    private ProtectionRules() {
    }

    public static boolean allows(Action action, Spot spot) {
        if (spot.claimId() == NO_CLAIM || spot.actorAuthorized()) {
            return true;
        }
        return switch (action) {
            case PLACE, OPEN_DOOR, USE_REDSTONE, HAMMER -> false;
            case BREAK -> !spot.structure();          // il terreno si scava, la struttura serve un Breach Core
            case OPEN_CONTAINER -> true;              // chi entra nella base apre le chest, come in Rust
        };
    }

    /** Esplosioni, fuoco, wither e simili: la struttura di un claim non si tocca, il terreno sì. */
    public static boolean externalDamageAllowed(Spot spot) {
        return spot.claimId() == NO_CLAIM || !spot.structure();
    }

    /**
     * Un pistone muove blocchi solo dentro il proprio claim (o fuori da ogni claim), e mai un Tool Cupboard: il claim
     * resterebbe senza cupboard nella sua posizione.
     */
    public static boolean pistonAllowed(long pistonClaim, long[] movedClaims, boolean movesCupboard) {
        if (movesCupboard) {
            return false;
        }
        for (long claim : movedClaims) {
            if (claim != pistonClaim) {
                return false;
            }
        }
        return true;
    }

    /** Un fluido non entra in un claim diverso da quello da cui parte. */
    public static boolean flowAllowed(long fromClaim, long toClaim) {
        return toClaim == NO_CLAIM || toClaim == fromClaim;
    }
}
