package net.shattergui.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shattergui.ShatterGuiClient;
import net.shattergui.fx.FxPackage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the effect editor remembers between sessions ({@code config/shatter_gui/editor.json}): whether the short tour
 * was seen, the sample interface, the last effect opened. Separate from the mod's settings, so it never makes them
 * "unsaved".
 */
final class EditorState {
    boolean tourDone;
    String sample = "chest";
    String lastEffect = "";
    private final Path file;

    private EditorState(Path file) {
        this.file = file;
    }

    static EditorState load(Path file) {
        EditorState state = new EditorState(file);
        try {
            if (Files.isRegularFile(file) && Files.size(file) < 4096) {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                state.tourDone = o.has("tourDone") && o.get("tourDone").getAsBoolean();
                state.sample = o.has("sample") ? o.get("sample").getAsString() : "chest";
                state.lastEffect = o.has("lastEffect") ? o.get("lastEffect").getAsString() : "";
            }
        } catch (IOException | RuntimeException e) {
            ShatterGuiClient.LOGGER.info("Effect editor state not readable, starting fresh: {}", e.toString());
        }
        return state;
    }

    void save() {
        JsonObject o = new JsonObject();
        o.addProperty("tourDone", tourDone);
        o.addProperty("sample", sample);
        o.addProperty("lastEffect", lastEffect);
        try {
            FxPackage.writeAtomic(file, FxDoc.pretty(o));
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.warn("Could not save the effect editor state: {}", e.toString());
        }
    }
}
