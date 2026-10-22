package org.breachsmp.core.platform.season;

import org.breachsmp.core.api.data.DataAccessException;
import org.breachsmp.core.api.season.SeasonService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Stagione corrente letta all'avvio da {@code current_season} (spec §12). Il cambio di stagione richiede un riavvio (M9). */
public record CurrentSeason(int currentSeasonId) implements SeasonService {

    private static final String SELECT = "SELECT season_id FROM current_season WHERE singleton = 1";

    /** Legge la stagione corrente (bloccante: solo all'avvio, D-10). Nessuna riga → avvio fallito (fail-closed). */
    public static CurrentSeason load(DataSource dataSource) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("Nessuna stagione corrente nel database (tabella current_season vuota)");
            }
            return new CurrentSeason(rs.getInt(1));
        } catch (SQLException e) {
            throw new DataAccessException("Impossibile leggere la stagione corrente: " + e.getMessage(), e);
        }
    }
}
