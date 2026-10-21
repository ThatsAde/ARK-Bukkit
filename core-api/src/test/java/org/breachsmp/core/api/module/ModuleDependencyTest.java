package org.breachsmp.core.api.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleDependencyTest {

    @Test
    void rejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleDependency(null));
    }

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleDependency("  "));
    }

    @Test
    void onCreatesDependency() {
        assertEquals("economy", ModuleDependency.on("economy").moduleId());
    }
}
