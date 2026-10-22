package org.breachsmp.core.economy.command;

import org.breachsmp.core.api.command.ChatLine;
import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.ModuleCommand;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.LedgerService;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.SystemAccount;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;
import org.breachsmp.core.api.lock.PlayerLock;
import org.breachsmp.core.api.player.PlayerProfile;
import org.breachsmp.core.api.player.PlayerService;
import org.breachsmp.core.domain.economy.FeePolicy;
import org.breachsmp.core.domain.economy.Money;
import org.breachsmp.core.domain.ratelimit.SlidingWindowLimiter;
import org.breachsmp.core.economy.EconomyTunables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** {@code /pay <giocatore> <importo>} (spec §5, E4, E9, E16): tassa tunable verso SYS_TAX_POOL, rate limit, lock per giocatore. */
public final class PayCommand implements ModuleCommand {

    public static final String PERMISSION = "breach.pay";
    public static final long MIN_AMOUNT = Money.UNITS_PER_COIN;

    private final LedgerService ledger;
    private final PlayerService players;
    private final PlayerLock lock;
    private final EconomyTunables tunables;
    private final SlidingWindowLimiter limiter;

    public PayCommand(LedgerService ledger, PlayerService players, PlayerLock lock, EconomyTunables tunables,
                      SlidingWindowLimiter limiter) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.players = Objects.requireNonNull(players, "players");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.tunables = Objects.requireNonNull(tunables, "tunables");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    @Override
    public String label() {
        return "pay";
    }

    @Override
    public String description() {
        return "Paga un altro giocatore";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public CompletableFuture<CommandResult> execute(CommandInvocation invocation) {
        // Parte sincrona (main thread): solo parsing e controlli in memoria
        if (invocation.args().size() != 2) {
            return done(CommandResult.error("Uso: /pay <giocatore> <importo>"));
        }
        long amount;
        try {
            amount = Money.parse(invocation.args().get(1));
        } catch (IllegalArgumentException e) {
            return done(CommandResult.error(e.getMessage()));
        }
        if (amount < MIN_AMOUNT) {
            return done(CommandResult.error("Il pagamento minimo è " + Money.format(MIN_AMOUNT) + "."));
        }
        UUID payer = invocation.senderId();
        if (!limiter.tryAcquire(payer, tunables.payRatePerMinute().get())) {
            return done(CommandResult.error("Troppi pagamenti in poco tempo: riprova tra un minuto."));
        }
        String targetName = invocation.args().getFirst();
        AccountRef payerAccount = AccountRef.player(payer);
        return players.findByName(targetName).thenCompose(found -> {
            if (found.isEmpty()) {
                return done(CommandResult.error("Giocatore non trovato: " + targetName + " (deve essere entrato almeno una volta)."));
            }
            PlayerProfile target = found.get();
            if (target.uuid().equals(payer)) {
                return done(CommandResult.error("Non puoi pagare te stesso."));
            }
            FeePolicy.Split split = FeePolicy.split(amount, tunables.transferFeeBp().get());
            TxRequest request = request(payer, invocation.senderName(), target, amount, split);
            return lock.withLock(payer, () -> ledger.execute(request)).handle((result, error) -> error != null
                    ? CommandResult.error(EconomyMessages.failure(error, payerAccount))
                    : success(invocation.senderName(), target, amount, split));
        }).exceptionally(error -> CommandResult.error(EconomyMessages.failure(error, payerAccount)));
    }

    @Override
    public List<String> suggest(List<String> args, List<String> onlinePlayers) {
        if (args.size() != 1) {
            return List.of();
        }
        String prefix = args.getFirst().toLowerCase(Locale.ROOT);
        return onlinePlayers.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static TxRequest request(UUID payer, String payerName, PlayerProfile target, long amount, FeePolicy.Split split) {
        List<Leg> legs = new ArrayList<>();
        legs.add(new Leg(AccountRef.player(payer), -amount));
        legs.add(new Leg(AccountRef.player(target.uuid()), split.net()));
        if (split.fee() > 0) {
            legs.add(new Leg(AccountRef.system(SystemAccount.TAX_POOL), split.fee()));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("from", payerName);
        meta.put("to", target.name());
        return new TxRequest(UUID.randomUUID(), TxKinds.PAY, legs, meta, payer); // un comando = un'operazione (E9)
    }

    private static CommandResult success(String payerName, PlayerProfile target, long amount, FeePolicy.Split split) {
        List<ChatLine> lines = new ArrayList<>();
        lines.add(ChatLine.success("Hai inviato " + Money.format(amount) + " a " + target.name() + "."));
        if (split.fee() > 0) {
            lines.add(ChatLine.muted("Tassa trattenuta: " + Money.format(split.fee()) + "; " + target.name()
                    + " riceve " + Money.format(split.net()) + "."));
        }
        return new CommandResult(lines, List.of())
                .withNotice(target.uuid(), ChatLine.success("Hai ricevuto " + Money.format(split.net()) + " da " + payerName + "."));
    }

    private static CompletableFuture<CommandResult> done(CommandResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
