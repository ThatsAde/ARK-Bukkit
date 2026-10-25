package org.breachsmp.core.api.territory;

import java.util.Optional;
import java.util.UUID;

/** Territorio visto dagli altri moduli (M3: gate e blast del raid). Nessun tipo Bukkit. */
public interface TerritoryService {

    /** Claim che copre il blocco (il più vicino al cupboard se più d'uno). Solo main thread: legge l'indice in memoria. */
    Optional<ClaimView> claimAt(UUID world, int x, int y, int z);

    boolean isAuthorized(long claimId, UUID player);

    /** Tier del materiale (nome Bukkit, es. {@code OAK_PLANKS}); per un materiale non configurato il tier di default (T10). */
    int tierOf(String materialKey);

    /** HP massimi di un tier (T10). */
    int hpOfTier(int tier);
}
