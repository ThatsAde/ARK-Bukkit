package org.breachsmp.core.api.util;

import java.util.Map;
import java.util.Objects;

/** Serializza una mappa piatta in un oggetto JSON. Valori ammessi: testo, interi, booleani, null (mai decimali: I3). */
public final class JsonText {

    private JsonText() {
    }

    public static String write(Map<String, ?> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            quote(out, Objects.requireNonNull(entry.getKey(), "chiave JSON nulla"));
            out.append(':');
            switch (entry.getValue()) {
                case null -> out.append("null");
                case String text -> quote(out, text);
                case Boolean flag -> out.append(flag);
                case Long number -> out.append(number);
                case Integer number -> out.append(number);
                case Short number -> out.append(number);
                case Byte number -> out.append(number);
                default -> throw new IllegalArgumentException("Valore JSON non ammesso per '" + entry.getKey() + "': "
                        + entry.getValue().getClass().getSimpleName() + " (solo testo, interi, booleani)");
            }
        }
        return out.append('}').toString();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
