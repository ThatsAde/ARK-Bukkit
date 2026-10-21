package org.breachsmp.core.platform.module;

import org.breachsmp.core.platform.concurrent.PlatformExecutors;
import org.breachsmp.core.platform.service.SimpleServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultModuleContextTest {

    @Test
    void syncRunsTheTaskOnTheMainExecutor() throws Exception {
        AtomicInteger mainRuns = new AtomicInteger();
        Executor main = task -> {
            mainRuns.incrementAndGet();
            task.run();
        };
        try (PlatformExecutors executors = new PlatformExecutors(main, 1, 1)) {
            var context = new DefaultModuleContext("economy", new SimpleServiceRegistry(), executors, Logger.getLogger("test"));
            assertEquals(42, context.sync(() -> 42).get(5, SECONDS));
            assertEquals(1, mainRuns.get());
        }
    }
}
