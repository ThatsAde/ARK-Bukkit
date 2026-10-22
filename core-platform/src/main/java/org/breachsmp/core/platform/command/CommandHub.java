package org.breachsmp.core.platform.command;

import org.breachsmp.core.api.command.AdminSubcommand;
import org.breachsmp.core.api.command.CommandRegistry;
import org.breachsmp.core.api.command.ModuleCommand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Raccoglie i comandi dei moduli finché la piattaforma non li registra in Paper ({@link #close()}). */
public final class CommandHub implements CommandRegistry {

    /** Sottocomandi di {@code /breach} della piattaforma: un modulo non può usarne il nome. */
    static final Set<String> RESERVED = Set.of("health", "tunable");

    private static final Pattern LABEL = Pattern.compile("[a-z][a-z0-9_-]*");

    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();
    private final Set<String> usedLabels = new HashSet<>();
    private final Map<String, AdminSubcommand> admin = new LinkedHashMap<>();
    private boolean closed;

    @Override
    public synchronized void register(ModuleCommand command) {
        ensureOpen();
        List<String> labels = new ArrayList<>();
        labels.add(command.label());
        labels.addAll(command.aliases());
        for (String label : labels) {
            if (label == null || !LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("Nome di comando non valido: '" + label + "' (formato [a-z][a-z0-9_-]*)");
            }
            if (usedLabels.contains(label)) {
                throw new IllegalStateException("Nome di comando già usato: " + label);
            }
        }
        usedLabels.addAll(labels);
        commands.put(command.label(), command);
    }

    @Override
    public synchronized void registerAdmin(AdminSubcommand subcommand) {
        ensureOpen();
        String name = Objects.requireNonNull(subcommand.name(), "name");
        if (RESERVED.contains(name) || admin.containsKey(name)) {
            throw new IllegalStateException("Sottocomando /breach già usato: " + name);
        }
        admin.put(name, subcommand);
    }

    /** Chiude le registrazioni: dopo, la piattaforma ha già registrato i comandi in Paper. */
    public synchronized void close() {
        closed = true;
    }

    public synchronized List<ModuleCommand> commands() {
        return List.copyOf(commands.values());
    }

    public synchronized List<AdminSubcommand> adminSubcommands() {
        return List.copyOf(admin.values());
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Registrazione dei comandi chiusa: registra in onLoad o onEnable");
        }
    }
}
