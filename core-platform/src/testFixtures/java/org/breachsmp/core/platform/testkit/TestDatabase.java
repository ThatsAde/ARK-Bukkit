package org.breachsmp.core.platform.testkit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.breachsmp.core.platform.db.Migrator;
import org.flywaydb.core.Flyway;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MariaDBContainer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MariaDB per i test di integrazione ({@code *IT}).
 * <p>
 * Ordine di scelta: database esterno se è impostata {@code BREACH_IT_JDBC_URL} (con {@code BREACH_IT_DB_USER} e
 * {@code BREACH_IT_DB_PASSWORD}), altrimenti container Testcontainers {@code mariadb:11.4} se Docker è disponibile,
 * altrimenti il test viene saltato (D-05, D-13).
 * <p>
 * I test svuotano lo schema con Flyway {@code clean}: per sicurezza lo schema esterno deve finire con {@code _it}.
 */
public final class TestDatabase {

    public static final String URL_ENV = "BREACH_IT_JDBC_URL";
    public static final String USER_ENV = "BREACH_IT_DB_USER";
    public static final String PASSWORD_ENV = "BREACH_IT_DB_PASSWORD";

    private static MariaDBContainer<?> container;

    private TestDatabase() {
    }

    /** Apre un pool sul database di test, oppure salta il test se non ce n'è uno. */
    public static HikariDataSource open() {
        HikariConfig config = new HikariConfig();
        String url = System.getenv(URL_ENV);
        if (url != null && !url.isBlank()) {
            if (!isDisposableSchema(url)) {
                throw new IllegalStateException(URL_ENV + " deve puntare a uno schema che finisce con '_it' "
                        + "(i test lo svuotano): " + url);
            }
            config.setJdbcUrl(url);
            config.setUsername(System.getenv(USER_ENV));
            config.setPassword(System.getenv(PASSWORD_ENV));
        } else {
            MariaDBContainer<?> db = container();
            config.setJdbcUrl(db.getJdbcUrl());
            config.setUsername(db.getUsername());
            config.setPassword(db.getPassword());
        }
        return new HikariDataSource(config);
    }

    /** Pool su uno schema vuoto con tutte le migrazioni applicate. Per gli IT che non testano le migrazioni stesse. */
    public static HikariDataSource migrated() {
        HikariDataSource dataSource = open();
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        new Migrator(dataSource, TestDatabase.class.getClassLoader()).migrate();
        return dataSource;
    }

    /** {@code true} se il nome dello schema nell'URL JDBC finisce con {@code _it}. */
    public static boolean isDisposableSchema(String jdbcUrl) {
        String rest = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return false;
        }
        String schema = rest.substring(slash + 1);
        int query = schema.indexOf('?');
        if (query >= 0) {
            schema = schema.substring(0, query);
        }
        return schema.endsWith("_it");
    }

    private static synchronized MariaDBContainer<?> container() {
        if (container == null) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Né " + URL_ENV + " né Docker disponibili: test di integrazione saltato");
            container = new MariaDBContainer<>("mariadb:11.4");
            container.start(); // fermato da Testcontainers (Ryuk) alla fine della JVM
        }
        return container;
    }
}
