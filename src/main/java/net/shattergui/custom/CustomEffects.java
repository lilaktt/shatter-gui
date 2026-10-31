package net.shattergui.custom;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.shattergui.ShatterGuiClient;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.game.FxEffects;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom effects from {@code config/shatter_gui/effects/*.json} and from resource packs
 * ({@code assets/<namespace>/shatter_gui/effects/*.json}). Reloaded with the resources (F3+T); for the same id the
 * player's config file wins over a resource pack. A file that cannot be used is skipped with one log line.
 */
public final class CustomEffects {
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("shatter_gui").resolve("effects");
    private static final String RESOURCE_PATH = "shatter_gui/effects";
    /** Largest file read (a valid effect is a few hundred bytes). */
    private static final long MAX_FILE_BYTES = 65_536;

    private static final Map<String, CustomEffectDef> FROM_CONFIG = new LinkedHashMap<>();
    private static final Map<String, CustomEffectDef> FROM_PACKS = new LinkedHashMap<>();

    private CustomEffects() {
    }

    /** Client start: writes the example files the first time, then reads the config folder. */
    public static void init() {
        writeExampleOnce();
        // Schema-2 effects load with the resources (their textures need the GPU): only the example is put in place here.
        FxEffects.init();
        reloadConfig();
    }

    /** Resource reload (also F3+T): resource packs and the config folder again. */
    public static void reload(ResourceManager resources) {
        FROM_PACKS.clear();
        for (Map.Entry<Identifier, Resource> entry : resources.listResources(RESOURCE_PATH,
                id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                String json = read(reader);
                if (FxParser.schemaOf(json) == FxLimits.SCHEMA) {
                    continue;
                }
                CustomEffectDef def = CustomEffectDef.parse(json, entry.getKey() + " (" + entry.getValue().sourcePackId() + ")");
                FROM_PACKS.put(def.id(), def);
            } catch (CustomEffectDef.InvalidEffectException | IOException e) {
                ShatterGuiClient.LOGGER.warn("Custom effect {} ignored: {}", entry.getKey(), e.getMessage());
            }
        }
        reloadConfig();
        FxEffects.reload(resources);
    }

    /** Reads the config folder and registers all custom effects (config over packs). */
    public static void reloadConfig() {
        FROM_CONFIG.clear();
        if (Files.isDirectory(DIRECTORY)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(DIRECTORY, "*.json")) {
                for (Path file : files) {
                    try {
                        if (Files.size(file) > MAX_FILE_BYTES) {
                            throw new CustomEffectDef.InvalidEffectException("file too large");
                        }
                        String json = Files.readString(file, StandardCharsets.UTF_8);
                        if (FxParser.schemaOf(json) == FxLimits.SCHEMA) {
                            // Schema 2: loaded by FxEffects.
                            continue;
                        }
                        CustomEffectDef def = CustomEffectDef.parse(json, file.getFileName().toString());
                        FROM_CONFIG.put(def.id(), def);
                    } catch (CustomEffectDef.InvalidEffectException | IOException e) {
                        ShatterGuiClient.LOGGER.warn("Custom effect {} ignored: {}", file.getFileName(), e.getMessage());
                    }
                }
            } catch (IOException e) {
                ShatterGuiClient.LOGGER.warn("Could not list {}", DIRECTORY, e);
            }
        }
        Map<String, CustomEffectDef> all = new LinkedHashMap<>(FROM_PACKS);
        all.putAll(FROM_CONFIG);
        ShatterEffects.setCustom(all.values());
        ShatterGuiClient.LOGGER.info("Custom effects: {} from config, {} from resource packs", FROM_CONFIG.size(),
                FROM_PACKS.size());
    }

    /** Custom effects from resource packs (shown, not editable; a config file with the same id wins). */
    public static Collection<CustomEffectDef> fromPacks() {
        return Collections.unmodifiableCollection(FROM_PACKS.values());
    }

    /** The player's own custom effects (editable in the editor). */
    public static Collection<CustomEffectDef> fromConfig() {
        return Collections.unmodifiableCollection(FROM_CONFIG.values());
    }

    /**
     * The editor's Save: writes the given effects as the config folder's content (removing files of effects that are
     * no longer there) and registers them.
     */
    public static void saveAll(Collection<CustomEffectDef> defs) {
        try {
            Files.createDirectories(DIRECTORY);
            Map<String, CustomEffectDef> keep = new LinkedHashMap<>();
            for (CustomEffectDef def : defs) {
                keep.put(def.id(), def);
                net.shattergui.fx.FxPackage.writeAtomic(DIRECTORY.resolve(def.id() + ".json"), def.toJsonString());
            }
            for (String id : FROM_CONFIG.keySet()) {
                if (!keep.containsKey(id)) {
                    Files.deleteIfExists(DIRECTORY.resolve(id + ".json"));
                }
            }
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.error("Could not save custom effects to {}", DIRECTORY, e);
        }
        reloadConfig();
    }

    private static void writeExampleOnce() {
        if (Files.exists(DIRECTORY)) {
            return;
        }
        Path example = DIRECTORY.resolve("example_ember_swirl.json");
        try {
            Files.createDirectories(DIRECTORY);
            if (!Files.exists(example)) {
                Files.writeString(example, EXAMPLE, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.warn("Could not write the example custom effect {}", example, e);
        }
    }

    private static String read(Reader reader) throws IOException, CustomEffectDef.InvalidEffectException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) > 0) {
            text.append(buffer, 0, n);
            if (text.length() > MAX_FILE_BYTES) {
                throw new CustomEffectDef.InvalidEffectException("file too large");
            }
        }
        return text.toString();
    }

    private static final String EXAMPLE = """
            {
              "schema": 1,
              "id": "example_ember_swirl",
              "name": "Ember swirl (example)",
              "base": "vortex",
              "params": {
                "vortexDuration": 30,
                "spiralTurns": 3.0
              },
              "particles": [
                { "particle": "minecraft:small_flame", "density": 0.6, "stage": "during" },
                { "particle": "minecraft:smoke", "density": 2.0, "stage": "end" }
              ],
              "sounds": [
                { "sound": "minecraft:block.fire.extinguish", "volume": 0.4, "pitch": 1.4, "moment": "end" }
              ]
            }
            """;
}
