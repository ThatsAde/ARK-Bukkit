package org.breachsmp.core.domain.economy;

import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.Leg;
import org.breachsmp.core.api.economy.TxKinds;
import org.breachsmp.core.api.economy.TxRequest;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ledger in memoria che usa le stesse regole pure del ledger JDBC (validate → ordine per id → balancesAfter). */
final class InMemoryLedger {

    private final Map<AccountRef, Long> balances = new HashMap<>();
    private final Map<AccountRef, Long> ids = new HashMap<>();

    void apply(TxRequest request) {
        LedgerRules.validate(request);
        List<Leg> legs = request.legs().stream().sorted(Comparator.comparingLong(leg -> id(leg.account()))).toList();
        List<LedgerRules.Posting> postings = legs.stream()
                .map(leg -> new LedgerRules.Posting(new LedgerRules.AccountState(id(leg.account()), leg.account(),
                        balances.getOrDefault(leg.account(), 0L), false), leg.delta()))
                .toList();
        List<Long> after = LedgerRules.balancesAfter(postings, TxKinds.bypassesFreeze(request.kind()));
        for (int i = 0; i < legs.size(); i++) {
            balances.put(legs.get(i).account(), after.get(i));
        }
    }

    Map<AccountRef, Long> snapshot() {
        return Map.copyOf(balances);
    }

    private long id(AccountRef ref) {
        return ids.computeIfAbsent(ref, r -> (long) ids.size() + 1);
    }
}
