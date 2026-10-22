package org.breachsmp.core.economy;

import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.player.LoginHook;
import org.breachsmp.core.api.player.QuitHook;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.util.IdemKeys;
import org.breachsmp.core.domain.ratelimit.SlidingWindowLimiter;
import org.breachsmp.core.economy.ledger.AccountDao;
import org.breachsmp.core.economy.ledger.BalanceCache;
import org.breachsmp.core.economy.ledger.JdbcLedgerService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Login: crea il conto della stagione, accredita il saldo iniziale una sola volta (D-24) e precarica la cache.
 * Un errore qui rifiuta il giocatore (fail-closed, spec §3). Uscita: libera cache e rate limit.
 */
public final class EconomyLogin implements LoginHook, QuitHook {

    private final SeasonService seasons;
    private final AccountDao accounts;
    private final JdbcLedgerService ledger;
    private final EconomyTunables tunables;
    private final BalanceCache cache;
    private final SlidingWindowLimiter payLimiter;

    public EconomyLogin(SeasonService seasons, AccountDao accounts, JdbcLedgerService ledger, EconomyTunables tunables,
                        BalanceCache cache, SlidingWindowLimiter payLimiter) {
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.tunables = Objects.requireNonNull(tunables, "tunables");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.payLimiter = Objects.requireNonNull(payLimiter, "payLimiter");
    }

    @Override
    public void onLogin(UUID uuid, String name) {
        AccountRef player = AccountRef.player(uuid);
        int season = seasons.currentSeasonId();
        accounts.ensure(season, player);
        long starting = tunables.startingBalance().get();
        if (starting > 0) {
            ledger.executeNow(new TxRequest(IdemKeys.of("start:" + season + ":" + uuid), TxKinds.STARTING_BALANCE,
                    List.of(new Leg(AccountRef.system(SystemAccount.FAUCET), -starting), new Leg(player, starting)), Map.of(), null));
        }
        ledger.balanceNow(player);
    }

    @Override
    public void onQuit(UUID uuid) {
        cache.evict(AccountRef.player(uuid));
        payLimiter.forget(uuid);
    }
}
