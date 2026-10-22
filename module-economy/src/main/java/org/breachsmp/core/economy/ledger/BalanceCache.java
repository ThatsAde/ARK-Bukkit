package org.breachsmp.core.economy.ledger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.breachsmp.core.api.economy.AccountRef;

import java.time.Duration;
import java.util.OptionalLong;

/** Saldi dei conti della stagione corrente (D-29): aggiornati solo dopo un commit o una lettura dal DB, mai in modo ottimistico. */
public final class BalanceCache {

    private record Versioned(long version, long balance) {
    }

    private final Cache<AccountRef, Versioned> cache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    public OptionalLong get(AccountRef account) {
        Versioned value = cache.getIfPresent(account);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value.balance());
    }

    /** Un commit arrivato in ritardo (versione più bassa) non sovrascrive un saldo più nuovo. */
    public void offer(AccountRef account, long version, long balance) {
        cache.asMap().merge(account, new Versioned(version, balance),
                (current, offered) -> offered.version() >= current.version() ? offered : current);
    }

    public void evict(AccountRef account) {
        cache.invalidate(account);
    }
}
