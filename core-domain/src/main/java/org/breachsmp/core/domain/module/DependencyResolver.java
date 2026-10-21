package org.breachsmp.core.domain.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Ordine di avvio dei moduli secondo le dipendenze dichiarate (spec §3). Funzione pura. */
public final class DependencyResolver {

    private DependencyResolver() {
    }

    /**
     * Restituisce gli id ordinati in modo che ogni modulo venga dopo le proprie dipendenze;
     * a parità di livello l'ordine è alfabetico, così l'avvio è deterministico.
     *
     * @param graph id del modulo → id delle sue dipendenze
     * @throws DependencyException se una dipendenza non è nel grafo o se c'è un ciclo
     */
    public static List<String> order(Map<String, ? extends Collection<String>> graph) {
        Objects.requireNonNull(graph, "graph");
        Map<String, Set<String>> deps = new TreeMap<>();
        graph.forEach((id, dependsOn) -> deps.put(id, new TreeSet<>(dependsOn)));

        deps.forEach((id, dependsOn) -> {
            for (String dep : dependsOn) {
                if (!deps.containsKey(dep)) {
                    throw new DependencyException("Il modulo '" + id + "' dipende da '" + dep
                            + "', che non è registrato o è disabilitato");
                }
            }
        });

        Map<String, Integer> pending = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        deps.forEach((id, dependsOn) -> {
            pending.put(id, dependsOn.size());
            for (String dep : dependsOn) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(id);
            }
        });

        TreeSet<String> ready = new TreeSet<>();
        pending.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        List<String> order = new ArrayList<>(deps.size());
        while (!ready.isEmpty()) {
            String id = ready.pollFirst();
            order.add(id);
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                if (pending.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() != deps.size()) {
            throw new DependencyException("Ciclo di dipendenze tra moduli: "
                    + String.join(" -> ", findCycle(deps, new HashSet<>(order))));
        }
        return List.copyOf(order);
    }

    /** Ogni nodo non risolto ha almeno una dipendenza non risolta: seguendole si ritorna per forza su un nodo già visto. */
    private static List<String> findCycle(Map<String, Set<String>> deps, Set<String> resolved) {
        String current = deps.keySet().stream().filter(id -> !resolved.contains(id)).findFirst().orElseThrow();
        List<String> path = new ArrayList<>();
        Map<String, Integer> positionInPath = new HashMap<>();
        while (!positionInPath.containsKey(current)) {
            positionInPath.put(current, path.size());
            path.add(current);
            current = deps.get(current).stream().filter(dep -> !resolved.contains(dep)).findFirst().orElseThrow();
        }
        List<String> cycle = new ArrayList<>(path.subList(positionInPath.get(current), path.size()));
        cycle.add(current);
        return cycle;
    }
}
