package org.breachsmp.core.economy.recon;

import org.breachsmp.core.api.audit.AuditEntry;
import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.economy.AccountRef;
import org.breachsmp.core.api.economy.OwnerType;
import org.breachsmp.core.api.job.Job;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.util.Uuids;
import org.breachsmp.core.economy.ledger.AccountDao;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Riconciliazione notturna (spec §5, E11, E14): ricalcola ogni saldo dalle {@code ledger_entry} e controlla che la somma
 * di tutti i saldi sia zero. Conto di un giocatore manomesso → congelato; conto di sistema → solo alert e health DEGRADED.
 */
public final class ReconciliationJob implements Job {

    public static final String ID = "economy.reconciliation";

    /** Un conto il cui saldo non coincide con la somma delle sue entry. */
    public record Mismatch(long accountId, AccountRef account, long stored, long computed) {
    }

    /** Esito di un'esecuzione. */
    public record Report(Instant at, long accounts, long totalBalance, List<Mismatch> mismatches) {

        public Report {
            mismatches = List.copyOf(mismatches);
        }

        public boolean clean() {
            return mismatches.isEmpty() && totalBalance == 0;
        }
    }

    private static final String MISMATCHES = """
            SELECT a.id, a.owner_type, a.owner_ref, a.balance, COALESCE(SUM(e.delta), 0) AS computed
            FROM account a LEFT JOIN ledger_entry e ON e.account_id = a.id
            WHERE a.season_id = ?
            GROUP BY a.id, a.owner_type, a.owner_ref, a.balance
            HAVING a.balance <> computed""";
    private static final String TOTAL = "SELECT COUNT(*), COALESCE(SUM(balance), 0) FROM account WHERE season_id = ?";

    private final DataSource dataSource;
    private final SeasonService seasons;
    private final AccountDao accounts;
    private final AuditService audit;
    private final Clock clock;
    private final Logger logger;
    private final DateTimeFormatter format;
    private volatile Report last;

    public ReconciliationJob(DataSource dataSource, SeasonService seasons, AccountDao accounts, AuditService audit,
                             Clock clock, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.format = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(clock.getZone());
    }

    @Override
    public void run() {
        runNow();
    }

    /** Esegue subito (bloccante, thread DB). */
    public Report runNow() {
        int season = seasons.currentSeasonId();
        Instant now = clock.instant();
        Report report = read(season, now);
        for (Mismatch mismatch : report.mismatches()) {
            logger.severe("Riconciliazione: saldo di " + mismatch.account() + " = " + mismatch.stored()
                    + ", entry = " + mismatch.computed());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("account", mismatch.account().toString());
            detail.put("stored", mismatch.stored());
            detail.put("computed", mismatch.computed());
            recordQuietly(new AuditEntry(null, "RECON_MISMATCH", "account:" + mismatch.accountId(), detail));
            if (mismatch.account().type() != OwnerType.SYSTEM && accounts.setFrozen(mismatch.accountId(), true, now)) {
                logger.severe("Riconciliazione: conto " + mismatch.account() + " congelato in attesa di verifica");
            }
        }
        if (report.totalBalance() != 0) {
            logger.severe("Riconciliazione: la somma dei saldi della stagione " + season + " è " + report.totalBalance() + " invece di 0");
            recordQuietly(new AuditEntry(null, "RECON_TOTAL", "season:" + season, Map.of("total", report.totalBalance())));
        }
        last = report;
        return report;
    }

    public Optional<Report> lastReport() {
        return Optional.ofNullable(last);
    }

    /** Ultimo report con mismatch su un conto di sistema o somma dei saldi diversa da zero (E14). */
    public boolean systemProblem() {
        Report report = last;
        return report != null && (report.totalBalance() != 0
                || report.mismatches().stream().anyMatch(m -> m.account().type() == OwnerType.SYSTEM));
    }

    public List<String> healthLines() {
        Report report = last;
        if (report == null) {
            return List.of("Riconciliazione: mai eseguita");
        }
        String when = format.format(report.at());
        if (report.clean()) {
            return List.of("Riconciliazione: OK (" + report.accounts() + " conti, " + when + ")");
        }
        String line = "Riconciliazione: " + report.mismatches().size() + " discrepanze (" + when + ")";
        return List.of(report.totalBalance() == 0 ? line : line + ", somma saldi " + report.totalBalance());
    }

    /** Saldi ed entry dalla stessa fotografia REPEATABLE READ: una transazione concorrente non crea falsi allarmi. */
    private Report read(int season, Instant now) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            c.setReadOnly(true);
            try {
                List<Mismatch> mismatches = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(MISMATCHES)) {
                    ps.setInt(1, season);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            AccountRef ref = new AccountRef(OwnerType.valueOf(rs.getString(2)), Uuids.fromBytes(rs.getBytes(3)));
                            mismatches.add(new Mismatch(rs.getLong(1), ref, rs.getLong(4), rs.getLong(5)));
                        }
                    }
                }
                long count;
                long total;
                try (PreparedStatement ps = c.prepareStatement(TOTAL)) {
                    ps.setInt(1, season);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        count = rs.getLong(1);
                        total = rs.getLong(2);
                    }
                }
                return new Report(now, count, total, mismatches);
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Riconciliazione fallita: " + e.getMessage(), e);
        }
    }

    private void recordQuietly(AuditEntry entry) {
        try {
            audit.recordNow(entry);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Audit della riconciliazione non scritto: " + entry, e);
        }
    }
}
