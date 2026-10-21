package org.breachsmp.core.platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    private static final String VALID = """
            config_version: 1
            database:
              host: db.local
              port: 3306
              name: breach_game
              user: bc_game
              password_file: secrets/db-password
              pool_size: 0
              max_lifetime_seconds: 1500
            modules:
              economy:
                enabled: true
              arena:
                enabled: false
            """;

    @TempDir
    Path dataDir;

    @Test
    void validConfigIsParsed() throws IOException {
        CoreConfig config = load(VALID);
        DatabaseConfig db = config.database();
        assertEquals(1, config.configVersion());
        assertEquals("jdbc:mariadb://db.local:3306/breach_game", db.jdbcUrl());
        assertEquals("bc_game", db.user());
        assertEquals(dataDir.resolve("secrets/db-password").normalize(), db.passwordFile());
        assertEquals(0, db.poolSize());
        assertEquals(1500, db.maxLifetimeSeconds());
        assertTrue(config.moduleEnabled("economy"));
        assertFalse(config.moduleEnabled("arena"));
        assertFalse(config.moduleEnabled("unknown"));
    }

    @Test
    void shippedDefaultConfigIsValid() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml non trovato sul classpath");
            Files.copy(in, dataDir.resolve("config.yml"));
        }
        CoreConfig config = ConfigLoader.load(dataDir.resolve("config.yml"), dataDir);
        assertEquals(ConfigLoader.CURRENT_VERSION, config.configVersion());
    }

    @Test
    void missingFileIsReported() {
        var ex = assertThrows(ConfigException.class, () -> ConfigLoader.load(dataDir.resolve("config.yml"), dataDir));
        assertEquals(List.of("File di configurazione mancante: " + dataDir.resolve("config.yml")), ex.errors());
    }

    @Test
    void malformedYamlIsReported() {
        List<String> errors = errorsOf("database: [");
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().startsWith("Impossibile leggere config.yml"));
    }

    @Test
    void emptyFileIsReported() {
        assertEquals(List.of("Il file deve contenere una mappa YAML alla radice"), errorsOf(""));
    }

    @Test
    void unsupportedVersionIsRejected() {
        assertEquals(List.of("config_version 2 non supportata da questa versione del plugin (attesa 1)"),
                errorsOf(VALID.replace("config_version: 1", "config_version: 2")));
    }

    @Test
    void missingDatabaseSectionIsASingleError() {
        assertEquals(List.of("Sezione mancante: database"), errorsOf("config_version: 1\n"));
    }

    @Test
    void allErrorsAreReportedTogether() {
        String yaml = """
                config_version: 1
                database:
                  host: db.local
                  port: 99999
                  name: breach_game
                  password_file: secrets/db-password
                  pool_size: -1
                  max_lifetime_seconds: 1500
                """;
        assertEquals(List.of(
                "'database.port' = 99999 fuori intervallo [1, 65535]",
                "Valore mancante: database.user",
                "'database.pool_size' = -1 fuori intervallo [0, 100]"), errorsOf(yaml));
    }

    @Test
    void modulesSectionIsOptional() throws IOException {
        CoreConfig config = load(VALID.substring(0, VALID.indexOf("modules:")));
        assertFalse(config.moduleEnabled("economy"));
    }

    @Test
    void moduleEntryMustBeASection() {
        String yaml = VALID.substring(0, VALID.indexOf("modules:")) + "modules:\n  economy: yes\n";
        assertEquals(List.of("'modules.economy' deve essere una sezione con 'enabled: true|false'"), errorsOf(yaml));
    }

    @Test
    void moduleIdMustBeLowercase() {
        String yaml = VALID.substring(0, VALID.indexOf("modules:")) + "modules:\n  Economy:\n    enabled: true\n";
        assertEquals(List.of("Id modulo non valido: 'Economy' (solo minuscole, cifre e '-')"), errorsOf(yaml));
    }

    @Test
    void effectivePoolSizeIsAutomaticWhenZero() {
        DatabaseConfig auto = new DatabaseConfig("h", 3306, "n", "u", dataDir, 0, 1500);
        assertEquals(4, auto.effectivePoolSize(2));
        assertEquals(10, auto.effectivePoolSize(16));
        DatabaseConfig fixed = new DatabaseConfig("h", 3306, "n", "u", dataDir, 7, 1500);
        assertEquals(7, fixed.effectivePoolSize(16));
    }

    @Test
    void readPasswordStripsTrailingNewline() throws IOException {
        Path file = dataDir.resolve("db-password");
        Files.writeString(file, "s3cret\n");
        assertEquals("s3cret", ConfigLoader.readPassword(file));
    }

    @Test
    void readPasswordFailsWhenFileIsMissing() {
        var ex = assertThrows(ConfigException.class, () -> ConfigLoader.readPassword(dataDir.resolve("nope")));
        assertTrue(ex.errors().getFirst().startsWith("File della password DB mancante"));
    }

    @Test
    void readPasswordFailsWhenFileIsEmpty() throws IOException {
        Path file = dataDir.resolve("db-password");
        Files.writeString(file, "\n");
        var ex = assertThrows(ConfigException.class, () -> ConfigLoader.readPassword(file));
        assertTrue(ex.errors().getFirst().startsWith("Il file della password DB è vuoto"));
    }

    private CoreConfig load(String yaml) throws IOException {
        Path file = dataDir.resolve("config.yml");
        Files.writeString(file, yaml);
        return ConfigLoader.load(file, dataDir);
    }

    private List<String> errorsOf(String yaml) {
        return assertThrows(ConfigException.class, () -> load(yaml)).errors();
    }
}
