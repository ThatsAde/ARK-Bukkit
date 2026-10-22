package org.breachsmp.core.api.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTextTest {

    @Test
    void writesStringsIntegersBooleansAndNullInOrder() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("reason", "rimborso \"bug\"\n");
        values.put("amount", 1250L);
        values.put("count", 3);
        values.put("ok", true);
        values.put("none", null);
        assertEquals("{\"reason\":\"rimborso \\\"bug\\\"\\n\",\"amount\":1250,\"count\":3,\"ok\":true,\"none\":null}",
                JsonText.write(values));
    }

    @Test
    void escapesControlCharactersAndBackslash() {
        assertEquals("{\"k\":\"a\\\\b\\u0001\"}", JsonText.write(Map.of("k", "a\\b\u0001")));
    }

    @Test
    void rejectsFloatingPointAndOtherTypes() {
        assertThrows(IllegalArgumentException.class, () -> JsonText.write(Map.of("x", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> JsonText.write(Map.of("x", List.of())));
    }

    @Test
    void emptyMapIsEmptyObject() {
        assertEquals("{}", JsonText.write(Map.of()));
    }
}
