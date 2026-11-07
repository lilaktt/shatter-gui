package net.shattergui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every screen the mod has seen, with what the editor shows for it: the title it had the first time it was seen (in
 * the language of that moment, e.g. "Chest", "Furnace", a renamed chest's name), the mod it belongs to and its class.
 * Kept in {@code config/shatter_gui/screens.json} across sessions; only for display, the config itself still uses the
 * screen keys.
 */
public final class ScreenCatalog {
    public static final String MINECRAFT = "minecraft";
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("shatter_gui").resolve("screens.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_TITLE = 64;

    /** A seen screen: its key (menu id or class name), title, owning mod id and class name. */
    public record Entry(String key, String title, String modId, String className) {
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private ScreenCatalog() {
    }

    public static void load() {
        ENTRIES.clear();
        if (!Files.exists(PATH)) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                ENTRIES.put(e.getKey(), new Entry(e.getKey(), string(o, "title", ""), string(o, "mod", MINECRAFT),
                        string(o, "class", "")));
            }
        } catch (IOException | RuntimeException e) {
            ShatterGuiClient.LOGGER.warn("Could not read {}: {}", PATH, e.toString());
        }
    }

    /** Called when a screen opens or closes: adds it the first time it is seen (later titles do not replace it). */
    public static void record(Screen screen, List<String> keys) {
        String key = keys.getFirst();
        if (ENTRIES.containsKey(key)) {
            return;
        }
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString().strip();
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }
        ENTRIES.put(key, new Entry(key, title, owner(key, screen.getClass()), screen.getClass().getName()));
        save();
    }

    public static Collection<Entry> entries() {
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    public static Entry get(String key) {
        return ENTRIES.get(key);
    }

    /** The display name of a mod id ("Minecraft" for vanilla), or the id itself if no such mod is loaded. */
    public static String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getName()).orElse(modId);
    }

    /**
     * The mod a screen belongs to: the namespace of a menu id ("minecraft:furnace" → minecraft), otherwise the mod whose
     * jar or folder the screen's class was loaded from.
     */
    public static String owner(String key, Class<?> type) {
        int colon = key.indexOf(':');
        if (colon > 0) {
            return key.substring(0, colon);
        }
        return ownerOfClass(type);
    }

    /** As {@link #owner(String, Class)} for a key alone (a configured screen that was never seen). */
    public static String ownerOfKey(String key) {
        int colon = key.indexOf(':');
        if (colon > 0) {
            return key.substring(0, colon);
        }
        Entry entry = ENTRIES.get(key);
        if (entry != null) {
            return entry.modId();
        }
        return key.startsWith("net.minecraft.") || !key.contains(".") ? MINECRAFT : "?";
    }

    private static String ownerOfClass(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.")) {
            return MINECRAFT;
        }
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
                for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                    for (Path path : mod.getOrigin().getPaths()) {
                        if (path.toAbsolutePath().normalize().equals(location)) {
                            return mod.getMetadata().getId();
                        }
                    }
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            // Unknown origin: shown as an unknown mod.
        }
        return "?";
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Entry entry : ENTRIES.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("title", entry.title());
            o.addProperty("mod", entry.modId());
            o.addProperty("class", entry.className());
            root.add(entry.key(), o);
        }
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.warn("Could not write {}: {}", PATH, e.toString());
        }
    }

    private static String string(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }
}
