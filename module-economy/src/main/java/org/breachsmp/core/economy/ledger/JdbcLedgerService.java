package org.breachsmp.core.economy.ledger;

import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.EntryView;
import org.breachsmp.core.api.economy.IdempotencyConflictException;
import org.breachsmp.core.api.economy.LedgerException;
import org.breachsmp.core.api.economy.LedgerService;
import org.breachsmp.core.api.economy.LedgerTxCommitted;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.economy.TxResult;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.domain.economy.LedgerRules;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Ledger a partita doppia su MariaDB (spec §5, D-20): append-only, idempotente, lock in ordine di id, retry su deadlock. */
public final class JdbcLedgerService implements LedgerService {

    /** 1 tentativo + 3 retry (spec §4). */
    static final int MAX_ATTEMPTS = 4;
    /** Deadlock, lock wait timeout, record cambiato dopo la lettura. */
    private static final Set<Integer> RETRYABLE = Set.of(1213, 1205, 1020);

    private final DataSource dataSource;
    private final Executor db;
    private final SeasonService seasons;
    private final AccountDao accounts;
    private final BalanceCache cache;
    private final Clock clock;
    private final Logger logger;
    private final List<Consumer<LedgerTxCommitted>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong retries = new AtomicLong();

    public JdbcLedgerService(DataSource dataSource, Executor db, SeasonService seasons, AccountDao accounts,
                             BalanceCache cache, Clock clock, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.db = Objects.requireNonNull(db, "db");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<TxResult> execute(TxRequest request) {
        return CompletableFuture.supplyAsync(() -> executeNow(request), db);
    }

    @Override
    public CompletableFuture<Long> balance(AccountRef account) {
        OptionalLong cached = cache.get(account);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.getAsLong());
        }
        return CompletableFuture.supplyAsync(() -> balanceNow(account), db);
    }

    @Override
    public void subscribe(Consumer<LedgerTxCommitted> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Saldo letto dal DB (bloccante); conto inesistente = 0. */
    public long balanceNow(AccountRef account) {
        Optional<AccountDao.Snapshot> snapshot = accounts.read(seasons.currentSeasonId(), account);
        snapshot.ifPresent(s -> cache.offer(account, s.version(), s.balance()));
        return snapshot.map(AccountDao.Snapshot::balance).orElse(0L);
    }

    /** Retry su deadlock dall'avvio: misura la contesa sulle righe calde (rischio §18). */
    public long deadlockRetries() {
        return retries.get();
    }

    /** Esegue sul thread corrente (bloccante): solo da thread che possono fare I/O. */
    public TxResult executeNow(TxRequest request) {
        LedgerRules.validate(request);
        int season = seasons.currentSeasonId();
        Map<AccountRef, Long> ids = accounts.ensure(season, request.legs().stream().map(Leg::account).toList());
        for (int attempt = 1; ; attempt++) {
            try {
                Committed committed = runOnce(request, season, ids);
                if (!committed.result().replayed()) {
                    afterCommit(committed);
                }
                return committed.result();
            } catch (SQLException e) {
                if (RETRYABLE.contains(e.getErrorCode()) && attempt < MAX_ATTEMPTS) {
                    retries.incrementAndGet();
                    pause(attempt);
                    continue;
                }
                throw new LedgerException("Transazione non eseguita per un errore del database: " + e.getMessage(), e);
            }
        }
    }

    private record Committed(TxResult result, List<Long> versions) {
    }

    private Committed runOnce(TxRequest request, int season, Map<AccountRef, Long> ids) throws SQLException {
        List<Leg> legs = request.legs().stream().sorted(Comparator.comparingLong(leg -> ids.get(leg.account()))).toList();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS); // DATETIME(3)
                OptionalLong txId = LedgerDao.insertTx(c, season, request, now);
                if (txId.isEmpty()) {
                    c.rollback();
                    TxResult replayed = replay(c, request);
                    c.rollback(); // chiude la transazione di sola lettura
                    return new Committed(replayed, List.of());
                }
                List<LockedAccount> locked = accounts.lockForUpdate(c, legs.stream().map(leg -> ids.get(leg.account())).toList());
                List<LedgerRules.Posting> postings = new ArrayList<>(legs.size());
                for (int i = 0; i < legs.size(); i++) {
                    postings.add(new LedgerRules.Posting(locked.get(i).state(), legs.get(i).delta()));
                }
                List<Long> balances = LedgerRules.balancesAfter(postings, TxKinds.bypassesFreeze(request.kind()));
                accounts.updateBalances(c, locked, balances);
                LedgerDao.insertEntries(c, txId.getAsLong(), locked, legs, balances);
                c.commit();

                List<EntryView> entries = new ArrayList<>(legs.size());
                List<Long> versions = new ArrayList<>(legs.size());
                for (int i = 0; i < legs.size(); i++) {
                    entries.add(new EntryView(legs.get(i).account(), legs.get(i).delta(), balances.get(i)));
                    versions.add(locked.get(i).version() + 1);
                }
                return new Committed(new TxResult(txId.getAsLong(), request.idemKey(), request.kind(), now, entries, false), versions);
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(c, e);
                throw e;
            }
        }
    }

    private TxResult replay(Connection c, TxRequest request) throws SQLException {
        StoredTx stored = LedgerDao.findByIdemKey(c, request.idemKey()).orElseThrow(() ->
                new IllegalStateException("Chiave " + request.idemKey() + " duplicata ma transazione non trovata"));
        if (!stored.kind().equals(request.kind())) {
            throw new IdempotencyConflictException(request.idemKey(), stored.kind(), request.kind());
        }
        return stored.toResult(true);
    }

    private void afterCommit(Committed committed) {
        List<EntryView> entries = committed.result().entries();
        for (int i = 0; i < entries.size(); i++) {
            cache.offer(entries.get(i).account(), committed.versions().get(i), entries.get(i).balanceAfter());
        }
        LedgerTxCommitted event = new LedgerTxCommitted(committed.result());
        for (Consumer<LedgerTxCommitted> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Un listener di LedgerTxCommitted ha lanciato un'eccezione", e);
            }
        }
    }

    private static void rollbackQuietly(Connection c, Exception cause) {
        try {
            c.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LedgerException("Transazione interrotta durante il retry", e);
        }
    }
}
