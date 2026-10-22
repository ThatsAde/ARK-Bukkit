package org.breachsmp.core.platform.tunable;

import org.breachsmp.core.api.tunable.Tunable;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TunableRegistryTest {

    /** Questi test non toccano il DB: qualsiasi chiamata al DataSource è un errore. */
    static final DataSource NO_DATABASE = (DataSource) Proxy.newProxyInstance(TunableRegistryTest.class.getClassLoader(),
            new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });

    private final TunableRegistry registry =
            new TunableRegistry(NO_DATABASE, Runnable::run, Clock.systemUTC(), Logger.getLogger("test"));

    @Test
    void registerValidatesKeyRangeAndDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> registry.registerLong("Economy.X", 0, 0, 10, "d"));
        assertThrows(IllegalArgumentException.class, () -> registry.registerLong("economy.x", 11, 0, 10, "d"));
        assertThrows(IllegalArgumentException.class, () -> registry.registerLong("economy.x", 0, 5, 1, "d"));
        registry.registerLong("economy.x", 3, 0, 10, "d");
        assertThrows(IllegalStateException.class, () -> registry.registerLong("economy.x", 3, 0, 10, "d"));
    }

    @Test
    void newTunableStartsAtDefaultAndListIsSortedByKey() {
        Tunable b = registry.registerLong("b.key", 7, 0, 10, "seconda");
        registry.registerLong("a.key", 1, 0, 10, "prima");
        assertEquals(7, b.get());
        assertEquals(List.of("a.key", "b.key"), registry.list().stream().map(TunableView::key).toList());
        assertEquals(new TunableView("b.key", 7, 7, 0, 10, "seconda"), registry.find("b.key").orElseThrow());
    }
}
