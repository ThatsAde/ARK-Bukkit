package org.breachsmp.core.platform;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
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
}
