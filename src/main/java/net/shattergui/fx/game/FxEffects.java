package net.shattergui.fx.game;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.shattergui.ShatterGuiClient;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxPackage;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;
import net.shattergui.params.BundledParams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Loads schema-2 effects: {@code config/shatter_gui/effects/*.json} (schema 2), packages
 * {@code config/shatter_gui/packages/*.shatterfx} (a ZIP with {@code effect.json} and {@code textures/*.png}) and
 * resource packs ({@code assets/<ns>/shatter_gui/effects/*.json}, textures in {@code assets/<ns>/shatter_gui/textures}).
 * Textures of loose files come from {@code config/shatter_gui/textures} or resource packs. For the same id a package
 * beats a config file, which beats a resource pack. Reloaded with the resources (F3+T) and from the editor.
 */
public final class FxEffects {
    public static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("shatter_gui");
    public static final Path EFFECTS = CONFIG.resolve("effects");
    public static final Path TEXTURES = CONFIG.resolve("textures");
    public static final Path PACKAGES = CONFIG.resolve("packages");
    /** Where the editor writes exported packages (not loaded from there, so an export never doubles an effect). */
    public static final Path EXPORTS = CONFIG.resolve("exports");
    private static final String PACK_EFFECTS = "shatter_gui/effects";
    private static final String PACK_TEXTURES = "shatter_gui/textures";
    private static final String EXAMPLE = "example_mask_spiral.shatterfx";
    private static final int MAX_CODE_LENGTH = 32_768;

    private static final Map<String, DataEffect> LOADED = new LinkedHashMap<>();
    private static final List<String> MESSAGES = new ArrayList<>();
    private static ResourceManager resources;

    private FxEffects() {
    }

    /**
     * Client start: puts the example package in place the first time. Loading waits for the resource reload at game
     * start (textures need the GPU).
     */
    public static void init() {
        writeExampleOnce();
    }

    /** Resource reload (F3+T). */
    public static void reload(ResourceManager manager) {
        resources = manager;
        reloadAll();
    }

    public static Collection<DataEffect> loaded() {
        return Collections.unmodifiableCollection(LOADED.values());
    }

    public static DataEffect get(String id) {
        return LOADED.get(id);
    }

    /** What the last load reported (errors and warnings, one line each), for the editor. */
    public static List<String> messages() {
        return List.copyOf(MESSAGES);
    }

    /** Reads everything again (also the editor's "Reload" button). */
    public static void reloadAll() {
        for (DataEffect effect : LOADED.values()) {
            effect.release();
        }
        LOADED.clear();
        MESSAGES.clear();
        if (resources == null) {
            resources = Minecraft.getInstance().getResourceManager();
        }
        loadBundled();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        fromResourcePacks(candidates);
        fromConfig(candidates);
        fromPackages(candidates);
        for (Candidate candidate : candidates.values()) {
            load(candidate);
        }
        ShatterEffects.setData(LOADED.values());
        ShatterGuiClient.LOGGER.info("Schema-2 effects: {} loaded ({} effect textures in memory)", LOADED.size(),
                FxTextures.loadedTotal());
    }

    /**
     * An effect made right now from a definition (the editor's "Test"), with textures from the config folder and
     * resource packs. It is not registered; free it with {@link #release} when it has played.
     */
    public static DataEffect build(net.shattergui.fx.FxDef def, List<String> warnings) {
        FxTextures textures = FxTextures.load(def, List.of(configTextures(), packTextures(null)));
        warnings.addAll(textures.warnings());
        return DataEffect.link(def, textures, "editor", warnings);
    }

    public static void release(DataEffect effect) {
        effect.release();
    }

    /** An effect to load: its JSON, where it came from, and where its textures are looked up (in order). */
    private record Candidate(String id, String json, String origin, List<FxTextures.Source> sources) {
    }

    private static void load(Candidate c) {
        FxParser.Result result = FxParser.parse(c.json());
        for (FxProblems.Problem warning : result.problems().warnings()) {
            message(false, c.origin() + ": " + warning);
        }
        if (!result.ok()) {
            message(true, c.origin() + " ignored: " + result.problems().errorSummary());
            return;
        }
        FxTextures textures = FxTextures.load(result.def(), c.sources());
        List<String> warnings = new ArrayList<>(textures.warnings());
        DataEffect effect = DataEffect.link(result.def(), textures, c.origin(), warnings);
        for (String warning : warnings) {
            message(false, c.origin() + ": " + warning);
        }
        if (ShatterEffects.isReserved(result.def().id)) {
            message(true, c.origin() + " ignored: id \"" + result.def().id + "\" is an effect of the mod");
            textures.release();
            return;
        }
        DataEffect old = LOADED.put(result.def().id, effect);
        if (old != null) {
            old.release();
        }
    }

    /**
     * The effects shipped with the mod ({@code assets/shatter_gui/effects/<id>.json} in the jar, read from the class
     * path, not through resource packs). They load first and their ids cannot be taken by other files. A problem in
     * one is logged as an error (the tests check there are none).
     */
    private static void loadBundled() {
        for (String id : BundledParams.ids()) {
            String path = "/assets/shatter_gui/effects/" + id + ".json";
            try (InputStream in = FxEffects.class.getResourceAsStream(path)) {
                if (in == null) {
                    message(true, path + " is missing from the mod");
                    continue;
                }
                FxParser.Result result = FxParser.parse(readText(in));
                if (!result.ok() || !id.equals(result.def().id)) {
                    message(true, path + " ignored: " + (result.ok() ? "id differs from the file name"
                            : result.problems().errorSummary()));
                    continue;
                }
                for (FxProblems.Problem warning : result.problems().warnings()) {
                    message(true, path + ": " + warning);
                }
                List<String> warnings = new ArrayList<>();
                FxTextures textures = FxTextures.load(result.def(), List.of(packTextures("shatter_gui")));
                warnings.addAll(textures.warnings());
                DataEffect effect = DataEffect.link(result.def(), textures, "Shatter GUI", true, warnings);
                for (String warning : warnings) {
                    message(true, path + ": " + warning);
                }
                LOADED.put(id, effect);
            } catch (IOException e) {
                message(true, path + " could not be read: " + e.getMessage());
            }
        }
    }

    /** The JSON of a shipped effect (for cloning it into the player's own), or null. */
    public static String bundledJson(String id) {
        DataEffect effect = LOADED.get(id);
        return effect != null && effect.bundled ? effect.def.json : null;
    }

    private static void message(boolean error, String text) {
        if (MESSAGES.size() < 200) {
            MESSAGES.add((error ? "error: " : "warning: ") + text);
        }
        if (error) {
            ShatterGuiClient.LOGGER.warn("Effect {}", text);
        } else {
            ShatterGuiClient.LOGGER.info("Effect {}", text);
        }
    }

    /** The id inside a JSON text without the full check (to apply precedence), or null. */
    private static String idOf(String json) {
        try {
            var root = com.google.gson.JsonParser.parseString(json);
            if (root.isJsonObject() && root.getAsJsonObject().has("id")) {
                return root.getAsJsonObject().get("id").getAsString();
            }
        } catch (RuntimeException e) {
            // Reported by the parser.
        }
        return null;
    }

    private static void put(Map<String, Candidate> candidates, String json, String origin, List<FxTextures.Source> sources) {
        String id = idOf(json);
        candidates.put(id != null ? id : "?" + origin, new Candidate(id, json, origin, sources));
    }

    // --- sources ---

    private static void fromResourcePacks(Map<String, Candidate> candidates) {
        if (resources == null) {
            return;
        }
        for (Map.Entry<Identifier, Resource> entry : resources.listResources(PACK_EFFECTS,
                id -> id.getPath().endsWith(".json")).entrySet()) {
            try (InputStream in = entry.getValue().open()) {
                String json = readText(in);
                if (FxParser.schemaOf(json) != FxLimits.SCHEMA) {
                    continue;
                }
                String namespace = entry.getKey().getNamespace();
                put(candidates, json, entry.getKey() + " (" + entry.getValue().sourcePackId() + ")",
                        List.of(packTextures(namespace)));
            } catch (IOException e) {
                message(true, entry.getKey() + " could not be read: " + e.getMessage());
            }
        }
    }

    private static void fromConfig(Map<String, Candidate> candidates) {
        if (!Files.isDirectory(EFFECTS)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(EFFECTS, "*.json")) {
            for (Path file : files) {
                if (Files.size(file) > FxLimits.MAX_JSON_BYTES) {
                    continue;
                }
                String json = Files.readString(file, StandardCharsets.UTF_8);
                if (FxParser.schemaOf(json) != FxLimits.SCHEMA) {
                    continue;
                }
                put(candidates, json, "effects/" + file.getFileName(), List.of(configTextures(), packTextures(null)));
            }
        } catch (IOException e) {
            message(true, "could not list " + EFFECTS + ": " + e.getMessage());
        }
    }

    private static void fromPackages(Map<String, Candidate> candidates) {
        if (!Files.isDirectory(PACKAGES)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(PACKAGES, "*.shatterfx")) {
            for (Path file : files) {
                String origin = "packages/" + file.getFileName();
                try {
                    Map<String, byte[]> entries = readPackage(file);
                    byte[] json = entries.get("effect.json");
                    if (json == null) {
                        message(true, origin + " ignored: no effect.json inside");
                        continue;
                    }
                    put(candidates, new String(json, StandardCharsets.UTF_8), origin,
                            List.of(zipTextures(entries), configTextures(), packTextures(null)));
                } catch (IOException e) {
                    message(true, origin + " ignored: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            message(true, "could not list " + PACKAGES + ": " + e.getMessage());
        }
    }

    /**
     * Reads a .shatterfx safely: at most {@link FxLimits#MAX_PACKAGE_ENTRIES} entries and
     * {@link FxLimits#MAX_PACKAGE_BYTES} unpacked, only {@code effect.json} and {@code textures/*.png}, no paths that
     * leave the package. Other entries are skipped.
     */
    static Map<String, byte[]> readPackage(Path file) throws IOException {
        return FxPackage.read(file);
    }

    private static FxTextures.Source zipTextures(Map<String, byte[]> entries) {
        return name -> entries.get("textures/" + name + ".png");
    }

    private static FxTextures.Source configTextures() {
        return name -> {
            if (name.contains(":")) {
                return null;
            }
            Path file = TEXTURES.resolve(name + ".png").normalize();
            if (!file.startsWith(TEXTURES) || !Files.isRegularFile(file)) {
                return null;
            }
            if (Files.size(file) > FxLimits.MAX_TEXTURE_BYTES) {
                throw new IOException("file over " + FxLimits.MAX_TEXTURE_BYTES / 1024 + " KB");
            }
            return Files.readAllBytes(file);
        };
    }

    /** Resource-pack textures: "ns:name" exactly, a plain name in {@code preferred} first, then any namespace. */
    private static FxTextures.Source packTextures(String preferred) {
        return name -> {
            if (resources == null) {
                return null;
            }
            List<Identifier> tries = new ArrayList<>();
            int colon = name.indexOf(':');
            if (colon > 0) {
                tries.add(Identifier.fromNamespaceAndPath(name.substring(0, colon),
                        PACK_TEXTURES + "/" + name.substring(colon + 1) + ".png"));
            } else {
                if (preferred != null) {
                    tries.add(Identifier.fromNamespaceAndPath(preferred, PACK_TEXTURES + "/" + name + ".png"));
                }
                for (String namespace : resources.getNamespaces()) {
                    tries.add(Identifier.fromNamespaceAndPath(namespace, PACK_TEXTURES + "/" + name + ".png"));
                }
            }
            for (Identifier id : tries) {
                Optional<Resource> resource = resources.getResource(id);
                if (resource.isPresent()) {
                    try (InputStream in = resource.get().open()) {
                        byte[] bytes = in.readNBytes(FxLimits.MAX_TEXTURE_BYTES + 1);
                        if (bytes.length > FxLimits.MAX_TEXTURE_BYTES) {
                            throw new IOException("file over " + FxLimits.MAX_TEXTURE_BYTES / 1024 + " KB");
                        }
                        return bytes;
                    }
                }
            }
            return null;
        };
    }

    private static String readText(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(FxLimits.MAX_JSON_BYTES + 1);
        if (bytes.length > FxLimits.MAX_JSON_BYTES) {
            throw new IOException("file too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // --- example and codes ---

    private static void writeExampleOnce() {
        if (Files.exists(PACKAGES)) {
            return;
        }
        try (InputStream in = FxEffects.class.getResourceAsStream("/assets/shatter_gui/examples/" + EXAMPLE)) {
            Files.createDirectories(PACKAGES);
            if (in != null) {
                Files.write(PACKAGES.resolve(EXAMPLE), in.readNBytes((int) FxLimits.MAX_PACKAGE_BYTES));
            }
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.warn("Could not write the example effect package: {}", e.toString());
        }
    }

    /** A code (gzip + base64) for an effect without textures, or null if it has textures (share the package instead). */
    public static String toCode(DataEffect effect) {
        if (!effect.def.textures.isEmpty()) {
            return null;
        }
        return codeOf(effect.def.json);
    }

    /** A code (gzip + base64) for any effect JSON; null if it cannot be made. */
    public static String codeOf(String json) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /** The JSON inside a code, or null if it is not one (or too large). */
    public static String fromCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() > MAX_CODE_LENGTH) {
            return null;
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(code.trim())))) {
            byte[] json = in.readNBytes(FxLimits.MAX_JSON_BYTES + 1);
            return json.length > FxLimits.MAX_JSON_BYTES ? null : new String(json, StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Saves an imported schema-2 effect (no textures) as {@code effects/<id>.json} and reloads. Returns the problems;
     * nothing is written when there are errors or the effect needs textures.
     */
    public static FxProblems importJson(String json) {
        FxParser.Result result = FxParser.parse(json);
        if (!result.ok()) {
            return result.problems();
        }
        if (!result.def().textures.isEmpty()) {
            result.problems().error("textures", "an effect with textures is shared as a .shatterfx package, not a code");
            return result.problems();
        }
        try {
            Files.createDirectories(EFFECTS);
            FxPackage.writeAtomic(EFFECTS.resolve(result.def().id + ".json"), json);
        } catch (IOException e) {
            result.problems().error("", "could not be saved: " + e.getMessage());
            return result.problems();
        }
        reloadAll();
        return result.problems();
    }
}
