package net.shattergui.editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Starting points for a new effect (the gallery): an empty one and schema-2 versions of the classic looks. Plain JSON
 * files in the mod ({@code assets/shatter_gui/editor/templates/<id>.json}), without textures, so they work at once.
 */
public final class Templates {
    public static final List<String> IDS = List.of("empty", "glass", "burn", "dissolve", "vortex", "rain");

    private Templates() {
    }

    /** "Crack off" → "crack_off": the translation key part of a name in a shipped template or effect. */
    public static String labelSlug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** The template's JSON, or an empty effect if it cannot be read. */
    public static String json(String id) {
        String path = "/assets/shatter_gui/editor/templates/" + id + ".json";
        try (InputStream in = Templates.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Falls back to the minimal effect below.
        }
        return "{\"schema\": 2, \"id\": \"new_effect\", \"name\": \"New effect\", \"layers\": []}";
    }
}
