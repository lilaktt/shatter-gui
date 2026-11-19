package net.shattergui.editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class Templates {
    public static final List<String> IDS = List.of("empty", "glass", "burn", "dissolve", "vortex", "rain");

    private Templates() {
    }

    public static String labelSlug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    public static String json(String id) {
        String path = "/assets/shatter_gui/editor/templates/" + id + ".json";
        try (InputStream in = Templates.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
        }
        return "{\"schema\": 2, \"id\": \"new_effect\", \"name\": \"New effect\", \"layers\": []}";
    }
}
