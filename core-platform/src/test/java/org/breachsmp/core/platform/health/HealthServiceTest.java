package org.breachsmp.core.platform.health;

import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.module.ModuleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthServiceTest {

    private PlatformExecutors executors;

    @BeforeEach
    void setUp() {
        executors = new PlatformExecutors(Runnable::run, 1, 1);
    }

    @AfterEach
    void tearDown() {
        executors.close();
    }

    @Test
    void databaseUpWhenConnectionIsValid() throws Exception {
        HealthReport report = service(dataSource(true)).check().get(5, SECONDS);
        assertTrue(report.databaseUp());
        assertEquals(Map.of(), report.modules());
        assertEquals(List.of("db", "compute", "market"), List.copyOf(report.queueDepths().keySet()));
    }

    @Test
    void databaseDownWhenConnectionIsInvalid() throws Exception {
        assertFalse(service(dataSource(false)).check().get(5, SECONDS).databaseUp());
    }

    @Test
    void databaseDownWhenConnectionFails() throws Exception {
        DataSource failing = proxyDataSource((proxy, method, args) -> {
            throw new SQLException("DB giù");
        });
        assertFalse(service(failing).check().get(5, SECONDS).databaseUp());
    }

    private HealthService service(DataSource dataSource) {
        ModuleRegistry modules = new ModuleRegistry(Logger.getLogger("HealthServiceTest"));
        modules.load(List.of(), id -> false, id -> {
            throw new AssertionError("nessun modulo da caricare");
        });
        return new HealthService(modules, dataSource, executors);
    }

    private static DataSource dataSource(boolean valid) {
        Connection connection = (Connection) Proxy.newProxyInstance(HealthServiceTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> valid;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return proxyDataSource((proxy, method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static DataSource proxyDataSource(InvocationHandler handler) {
        return (DataSource) Proxy.newProxyInstance(HealthServiceTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, handler);
    }
}
