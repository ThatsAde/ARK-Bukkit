package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.CommandInvocation;
import org.breachsmp.core.api.command.CommandResult;
import org.breachsmp.core.api.command.Tone;
import org.breachsmp.core.platform.tunable.TunableRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TunableSubcommandTest {

    private static final DataSource NO_DATABASE = (DataSource) Proxy.newProxyInstance(TunableSubcommandTest.class.getClassLoader(),
            new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });

    private final TunableRegistry registry = new TunableRegistry(NO_DATABASE, Runnable::run, Clock.systemUTC(), Logger.getLogger("test"));
    private final TunableSubcommand command = new TunableSubcommand(registry);

    @Test
    void listAndGetWithoutDatabase() throws Exception {
        registry.registerLong("economy.recon_hour", 4, 0, 23, "Ora della riconciliazione");
        CommandResult list = command.execute(invocation()).get(5, SECONDS);
        assertEquals("economy.recon_hour = 4 (default 4, intervallo [0, 23]) – Ora della riconciliazione",
                list.lines().getFirst().text());
        assertEquals(Tone.ERROR, command.execute(invocation("get", "nope")).get(5, SECONDS).lines().getFirst().tone());
        assertEquals(Tone.ERROR, command.execute(invocation("set", "economy.recon_hour")).get(5, SECONDS).lines().getFirst().tone());
    }

    @Test
    void emptyRegistrySaysSo() throws Exception {
        assertEquals("Nessun tunable registrato.", command.execute(invocation("list")).get(5, SECONDS).lines().getFirst().text());
    }

    private static CommandInvocation invocation(String... args) {
        return new CommandInvocation(null, "CONSOLE", List.of(args), Set.of());
    }
}
