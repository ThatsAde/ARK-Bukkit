package org.breachsmp.core.api.command;

/**
 * Registro dei comandi dei moduli. Registra in {@code onLoad} o {@code onEnable};
 * la piattaforma li registra in Paper dopo {@code enableAll()}, poi ogni registrazione è un errore ({@link IllegalStateException}).
 */
public interface CommandRegistry {

    void register(ModuleCommand command);

    void registerAdmin(AdminSubcommand subcommand);
}
