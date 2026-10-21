package org.breachsmp.core.domain.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyResolverTest {

    @Test
    void emptyGraphGivesEmptyOrder() {
        assertEquals(List.of(), DependencyResolver.order(Map.of()));
    }

    @Test
    void independentModulesAreSortedAlphabetically() {
        var graph = Map.of("territory", List.<String>of(), "economy", List.<String>of(), "arena", List.<String>of());
        assertEquals(List.of("arena", "economy", "territory"), DependencyResolver.order(graph));
    }

    @Test
    void dependenciesComeFirst() {
        var graph = Map.of(
                "items", List.of("economy", "territory"),
                "territory", List.<String>of(),
                "economy", List.<String>of());
        assertEquals(List.of("economy", "territory", "items"), DependencyResolver.order(graph));
    }

    @Test
    void diamondIsResolvedDeterministically() {
        var graph = Map.of(
                "market", List.of("items", "economy"),
                "items", List.of("economy"),
                "economy", List.<String>of());
        assertEquals(List.of("economy", "items", "market"), DependencyResolver.order(graph));
    }

    @Test
    void duplicateDependencyIsCountedOnce() {
        var graph = Map.of("items", List.of("economy", "economy"), "economy", List.<String>of());
        assertEquals(List.of("economy", "items"), DependencyResolver.order(graph));
    }

    @Test
    void missingDependencyFailsWithExplicitMessage() {
        var ex = assertThrows(DependencyException.class,
                () -> DependencyResolver.order(Map.of("items", List.of("economy"))));
        assertEquals("Il modulo 'items' dipende da 'economy', che non è registrato o è disabilitato", ex.getMessage());
    }

    @Test
    void cycleFailsWithPath() {
        var graph = Map.of("a", List.of("b"), "b", List.of("a"), "c", List.of("a"));
        var ex = assertThrows(DependencyException.class, () -> DependencyResolver.order(graph));
        assertEquals("Ciclo di dipendenze tra moduli: a -> b -> a", ex.getMessage());
    }

    @Test
    void selfDependencyIsACycle() {
        var ex = assertThrows(DependencyException.class,
                () -> DependencyResolver.order(Map.of("a", List.of("a"))));
        assertEquals("Ciclo di dipendenze tra moduli: a -> a", ex.getMessage());
    }
}
