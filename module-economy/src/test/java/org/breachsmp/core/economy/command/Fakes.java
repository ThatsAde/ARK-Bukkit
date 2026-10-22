package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.EntryView;
import org.breachsmp.core.api.economy.LedgerService;
import org.breachsmp.core.api.economy.LedgerTxCommitted;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.lock.PlayerLock;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.api.tunable.Tunable;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Servizi finti per i test dei comandi: niente DB, niente server. */
final class Fakes {

    private Fakes() {
    }

    /** Registra le richieste; risponde con {@link #nextFailure} se impostato, altrimenti con un risultato finto. */
    static final class FakeLedger implements LedgerService {

        final List<TxRequest> requests = new CopyOnWriteArrayList<>();
        final Map<AccountRef, Long> balances = new ConcurrentHashMap<>();
        volatile RuntimeException nextFailure;

        @Override
        public CompletableFuture<TxResult> execute(TxRequest request) {
            requests.add(request);
            if (nextFailure != null) {
                return CompletableFuture.failedFuture(nextFailure);
            }
            List<EntryView> entries = request.legs().stream().map(leg -> new EntryView(leg.account(), leg.delta(), 0)).toList();
            return CompletableFuture.completedFuture(
                    new TxResult(requests.size(), request.idemKey(), request.kind(), Instant.EPOCH, entries, false));
        }

        @Override
        public CompletableFuture<Long> balance(AccountRef account) {
            return CompletableFuture.completedFuture(balances.getOrDefault(account, 0L));
        }

        @Override
        public void subscribe(Consumer<LedgerTxCommitted> listener) {
        }
    }

    /** Ricerca per nome senza maiuscole, come la collation del DB. */
    static final class FakePlayers implements PlayerService {

        private final Map<String, PlayerProfile> byName = new ConcurrentHashMap<>();

        void add(PlayerProfile profile) {
            byName.put(profile.name().toLowerCase(Locale.ROOT), profile);
        }

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findByName(String name) {
            return CompletableFuture.completedFuture(Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT))));
        }

        @Override
        public CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
            return CompletableFuture.completedFuture(byName.values().stream().filter(p -> p.uuid().equals(uuid)).findFirst());
        }
    }

    /** Esegue subito l'azione (nessuna attesa). */
    static final PlayerLock DIRECT_LOCK = new PlayerLock() {
        @Override
        public <T> CompletableFuture<T> withLock(UUID player, Supplier<CompletableFuture<T>> action) {
            return action.get();
        }
    };

    static Tunable tunable(String key, long value) {
        return new Tunable() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public long get() {
                return value;
            }
        };
    }

    static CommandInvocation invocation(UUID sender, String name, Set<String> permissions, String... args) {
        return new CommandInvocation(sender, name, List.of(args), permissions);
    }
}
