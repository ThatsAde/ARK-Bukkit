package org.breachsmp.core.api.module;

/** Dipendenza dichiarata da un modulo verso un altro, per id. */
public record ModuleDependency(String moduleId) {

    public ModuleDependency {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId non può essere vuoto");
        }
    }

    /** Forma leggibile: {@code List.of(ModuleDependency.on("economy"))}. */
    public static ModuleDependency on(String moduleId) {
        return new ModuleDependency(moduleId);
    }
}
