package org.breachsmp.core.platform.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Legge e valida {@code config.yml}. Raccoglie tutti gli errori e li lancia insieme in una {@link ConfigException}. */
public final class ConfigLoader {

    public static final int CURRENT_VERSION = 1;

    private static final Pattern MODULE_ID = Pattern.compile("[a-z][a-z0-9-]*");

    private ConfigLoader() {
    }

    /**
     * @param file    il {@code config.yml}
     * @param dataDir cartella del plugin, base dei percorsi relativi
     */
    public static CoreConfig load(Path file, Path dataDir) {
        if (!Files.isRegularFile(file)) {
            throw new ConfigException(List.of("File di configurazione mancante: " + file));
        }
        Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false); // chiavi duplicate = errore, non "vince l'ultima"
            root = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (IOException | YAMLException e) {
            throw new ConfigException(List.of("Impossibile leggere " + file.getFileName() + ": " + e.getMessage()));
        }
        return parse(root, dataDir);
    }

    /** Legge la password del DB dal suo file (contiene solo la password; spazi e a capo finali vengono tolti). */
    public static String readPassword(Path file) {
        try {
            String password = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (password.isEmpty()) {
                throw new ConfigException(List.of("Il file della password DB è vuoto: " + file));
            }
            return password;
        } catch (NoSuchFileException e) {
            throw new ConfigException(List.of("File della password DB mancante: " + file
                    + " (crealo con permessi ristretti; deve contenere solo la password)"));
        } catch (IOException e) {
            throw new ConfigException(List.of("Impossibile leggere il file della password DB " + file + ": " + e.getMessage()));
        }
    }

    static CoreConfig parse(Object root, Path dataDir) {
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new ConfigException(List.of("Il file deve contenere una mappa YAML alla radice"));
        }
        List<String> errors = new ArrayList<>();

        int version = integer(rootMap, "", "config_version", 1, Integer.MAX_VALUE, errors);
        if (version > 0 && version != CURRENT_VERSION) {
            errors.add("config_version " + version + " non supportata da questa versione del plugin (attesa "
                    + CURRENT_VERSION + ")");
        }
        DatabaseConfig database = database(rootMap, dataDir, errors);
        Map<String, Boolean> modules = modules(rootMap, errors);

        if (!errors.isEmpty()) {
            throw new ConfigException(errors);
        }
        return new CoreConfig(version, database, modules);
    }

    private static DatabaseConfig database(Map<?, ?> root, Path dataDir, List<String> errors) {
        Object value = root.get("database");
        if (!(value instanceof Map<?, ?> db)) {
            errors.add(value == null ? "Sezione mancante: database" : "'database' deve essere una sezione (mappa)");
            return null;
        }
        String prefix = "database.";
        String host = string(db, prefix, "host", errors);
        int port = integer(db, prefix, "port", 1, 65_535, errors);
        String name = string(db, prefix, "name", errors);
        String user = string(db, prefix, "user", errors);
        String passwordFile = string(db, prefix, "password_file", errors);
        int poolSize = integer(db, prefix, "pool_size", 0, 100, errors);
        int maxLifetime = integer(db, prefix, "max_lifetime_seconds", 30, 28_000, errors);

        Path passwordPath = dataDir;
        try {
            passwordPath = dataDir.resolve(passwordFile).normalize();
        } catch (InvalidPathException e) {
            errors.add("'database.password_file' non è un percorso valido: " + passwordFile);
        }
        return new DatabaseConfig(host, port, name, user, passwordPath, poolSize, maxLifetime);
    }

    private static Map<String, Boolean> modules(Map<?, ?> root, List<String> errors) {
        Object value = root.get("modules");
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> modules)) {
            errors.add("'modules' deve essere una sezione (mappa)");
            return Map.of();
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : modules.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String path = "modules." + id;
            if (!MODULE_ID.matcher(id).matches()) {
                errors.add("Id modulo non valido: '" + id + "' (solo minuscole, cifre e '-')");
            } else if (!(entry.getValue() instanceof Map<?, ?> section)) {
                errors.add("'" + path + "' deve essere una sezione con 'enabled: true|false'");
            } else if (section.get("enabled") instanceof Boolean enabled) {
                result.put(id, enabled);
            } else {
                errors.add("'" + path + ".enabled' deve essere true o false");
            }
        }
        return result;
    }

    private static String string(Map<?, ?> section, String prefix, String key, List<String> errors) {
        Object value = section.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        String path = prefix + key;
        errors.add(value == null ? "Valore mancante: " + path : "'" + path + "' deve essere un testo non vuoto");
        return "";
    }

    private static int integer(Map<?, ?> section, String prefix, String key, int min, int max, List<String> errors) {
        Object value = section.get(key);
        String path = prefix + key;
        if (value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            if (number >= min && number <= max) {
                return (int) number;
            }
            errors.add("'" + path + "' = " + number + " fuori intervallo [" + min + ", " + max + "]");
        } else {
            errors.add(value == null ? "Valore mancante: " + path : "'" + path + "' deve essere un numero intero");
        }
        return 0;
    }
}
