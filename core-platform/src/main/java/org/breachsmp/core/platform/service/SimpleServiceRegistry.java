package org.breachsmp.core.platform.service;

import org.breachsmp.core.api.service.ServiceRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** {@link ServiceRegistry} in memoria, thread-safe. */
public final class SimpleServiceRegistry implements ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, T implementation) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(implementation, "implementation");
        Object previous = services.putIfAbsent(type, implementation);
        if (previous != null) {
            throw new IllegalStateException("Servizio già registrato: " + type.getName()
                    + " (da " + previous.getClass().getName() + ")");
        }
    }

    @Override
    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    @Override
    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("Servizio non registrato: " + type.getName()
                + ". Il modulo che lo fornisce è abilitato e dichiarato in dependencies()?"));
    }
}
