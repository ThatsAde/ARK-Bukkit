package org.breachsmp.core.platform.module;

import org.breachsmp.core.api.module.ARKModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleCatalogTest {

    @Test
    void catalogContainsEconomyAndTerritoryWithUniqueIds() {
        List<String> ids = ModuleCatalog.all().stream().map(ARKModule::id).toList();
        assertTrue(ids.contains("economy"), ids.toString());
        assertTrue(ids.contains("territory"), ids.toString());
        assertEquals(ids.size(), Set.copyOf(ids).size(), "id dei moduli duplicati: " + ids);
    }
}
