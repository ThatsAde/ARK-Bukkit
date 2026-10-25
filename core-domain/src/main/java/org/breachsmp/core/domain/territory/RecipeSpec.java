package org.breachsmp.core.domain.territory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ricetta shaped (Tool Cupboard, martello) come la convenzione vanilla: da 1 a 3 righe larghe uguali da 1 a 3
 * caratteri; lo spazio è uno slot vuoto, ogni altro carattere deve avere un ingrediente. Portata da ToolCupboards
 * {@code RecipeDefinition}.
 *
 * @param ingredients carattere → materiale Bukkit in maiuscolo
 */
public record RecipeSpec(List<String> pattern, Map<Character, String> ingredients) {

    public RecipeSpec {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(ingredients, "ingredients");
        if (pattern.isEmpty() || pattern.size() > 3) {
            throw new IllegalArgumentException("il pattern deve avere da 1 a 3 righe, trovate " + pattern.size());
        }
        int width = pattern.getFirst().length();
        if (width == 0 || width > 3) {
            throw new IllegalArgumentException("le righe del pattern devono avere da 1 a 3 caratteri, trovati " + width);
        }
        for (String row : pattern) {
            if (row.length() != width) {
                throw new IllegalArgumentException("tutte le righe del pattern devono avere la stessa larghezza");
            }
            for (char slot : row.toCharArray()) {
                if (slot != ' ' && !ingredients.containsKey(slot)) {
                    throw new IllegalArgumentException("il carattere '" + slot + "' del pattern non ha un ingrediente");
                }
            }
        }
        pattern = List.copyOf(pattern);
        ingredients = Map.copyOf(ingredients);
    }
}
