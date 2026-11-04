package org.breachsmp.core.platform;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginYmlTest {

    @Test
    void pluginYmlPointsToTheMainClassAndHasAnExpandedVersion() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml non trovato sul classpath");
            Map<String, Object> yml = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            assertEquals("BreachCore", yml.get("name"));
            assertEquals(ARKPlugin.class.getName(), yml.get("main"));
            String version = String.valueOf(yml.get("version"));
            assertFalse(version.contains("${"), "la versione deve essere espansa da Gradle: " + version);
            assertTrue(yml.containsKey("api-version"));
        }
    }

    @Test
    void adminPermissionsDefaultToOp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            Map<String, Object> yml = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> permissions = (Map<String, Map<String, Object>>) yml.get("permissions");
            assertEquals("op", permissions.get("breach.command").get("default"));
            assertEquals("op", permissions.get("breach.admin.health").get("default"));
            assertEquals("op", permissions.get("breach.admin.tunable").get("default"));
            for (String admin : List.of("breach.balance.others", "breach.admin.eco", "breach.admin.tx", "breach.admin.rollback",
                    "breach.admin.freeze", "breach.admin.recon", "breach.admin.claim", "breach.territory.bypass")) {
                assertEquals("op", permissions.get(admin).get("default"), admin);
            }
            assertEquals(true, permissions.get("breach.pay").get("default"));
            assertEquals(true, permissions.get("breach.balance").get("default"));
        }
    }
}
