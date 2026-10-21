package org.breachsmp.core.platform.module;

/** Un modulo ha lanciato un'eccezione in {@code onLoad} o {@code onEnable}: l'avvio del plugin fallisce (fail-closed). */
public final class ModuleStartupException extends RuntimeException {

    public ModuleStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
