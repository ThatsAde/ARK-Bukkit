package org.breachsmp.core.platform.health;

import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.module.ModuleRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Raccoglie lo stato di DB, moduli e code sul thread DB (mai sul main, I2). */
public final class HealthService {

    private static final int DB_PING_TIMEOUT_SECONDS = 2;

    private final ModuleRegistry modules;
    private final DataSource dataSource;
    private final PlatformExecutors executors;
    private final Supplier<List<String>> platformDetails;

    /** @param platformDetails righe di piattaforma (es. ultimo job), calcolate sul thread DB */
    public HealthService(ModuleRegistry modules, DataSource dataSource, PlatformExecutors executors,
                         Supplier<List<String>> platformDetails) {
        this.modules = Objects.requireNonNull(modules, "modules");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.platformDetails = Objects.requireNonNull(platformDetails, "platformDetails");
    }

    public CompletableFuture<HealthReport> check() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> details = new ArrayList<>(modules.details());
            details.addAll(platformDetails.get());
            return new HealthReport(pingDatabase(), modules.health(), details, executors.queueDepths());
        }, executors.db());
    }

    boolean pingDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_PING_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }
}
