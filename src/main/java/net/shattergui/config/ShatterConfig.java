package net.shattergui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.shattergui.ShatterGuiClient;
import net.shattergui.params.Param;
import net.shattergui.params.ParamStore;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.params.Preset;
import net.shattergui.params.Quality;
import net.shattergui.ui.Theme;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain JSON config at config/shatter_gui.json. The plain fields are the JSON keys; effect settings live in the
 * parameter registry ({@link Params}) and are stored only where they differ from the default, in
 * {@code general.params} and {@code effects.<effectId>.params}. Configs from before the registry (flat keys such as
 * {@code gravity} or {@code burnDurationTicks}) are read and moved there.
 */
public final class ShatterConfig implements ParamStore {
    public static final String APPLY_ALL = "all";
    public static final String APPLY_CONTAINERS = "containers";
    public static final String OPEN_NONE = "none";
    public static final String OPEN_FLY_FROM_BLOCK = "fly_from_block";
    /** "open:<id>": the data effect <id> played backwards, gathering the interface (if it can open screens). */
    public static final String OPEN_DATA_PREFIX = "open:";
    public static final String POLICY_FINISH_OLDEST = "finishOldest";
    public static final String POLICY_SKIP_NEW = "skipNew";
    public static final int MAX_CONCURRENT_LIMIT = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("shatter_gui.json");

    /** Flat keys of older configs → the parameter that replaced them. */
    private static final Map<String, Param<?>> LEGACY_KEYS = new LinkedHashMap<>();

    static {
        LEGACY_KEYS.put("gridColumns", Params.GLASS_COLUMNS);
        LEGACY_KEYS.put("gridRows", Params.GLASS_ROWS);
        LEGACY_KEYS.put("lifetimeTicks", Params.GLASS_LIFETIME);
        LEGACY_KEYS.put("fadeStartFraction", Params.GLASS_FADE_START);
        LEGACY_KEYS.put("gravity", Params.GLASS_GRAVITY);
        LEGACY_KEYS.put("airDrag", Params.GLASS_AIR_DRAG);
        LEGACY_KEYS.put("burstStrength", Params.GLASS_BURST);
        LEGACY_KEYS.put("spin", Params.GLASS_SPIN);
        LEGACY_KEYS.put("bounce", Params.GLASS_BOUNCE);
        LEGACY_KEYS.put("fullbright", Params.GLASS_FULLBRIGHT);
        LEGACY_KEYS.put("maxShards", Params.MAX_SHARDS);
        LEGACY_KEYS.put("burnDurationTicks", Params.BURN_DURATION);
        LEGACY_KEYS.put("smokeDurationTicks", Params.BURN_SMOKE_DURATION);
        LEGACY_KEYS.put("burnFlameDensity", Params.BURN_FLAME_DENSITY);
        LEGACY_KEYS.put("burnSmokeDensity", Params.BURN_SMOKE_DENSITY);
        LEGACY_KEYS.put("flyDurationTicks", Params.FLY_DURATION);
        LEGACY_KEYS.put("edgeDirection", Params.FLY_EDGE_DIRECTION);
        LEGACY_KEYS.put("dissolveDurationTicks", Params.DISSOLVE_DURATION);
        LEGACY_KEYS.put("dissolveOrigin", Params.DISSOLVE_ORIGIN);
        LEGACY_KEYS.put("particleDensity", Params.PARTICLE_DENSITY);
    }

    private static ShatterConfig instance = new ShatterConfig();

    /** Master switch: false makes the mod do nothing at all (one boolean check in each hook). */
    public boolean enabled = true;
    /** The effect used when a screen closes ("glass", "burn", "fly", "dissolve", …). Cycled with the keybind. */
    public String effect = "glass";
    /**
     * Effects the cycle key switches between (ids, in the order of the effect list). Empty: the key only says how to
     * add some. Older configs have none.
     */
    public List<String> favorites = new ArrayList<>();
    /**
     * The effect used when a screen opens: "none", "fly_from_block" (out of the block or entity just used) or
     * "open:<id>" (that effect played backwards).
     */
    public String openEffect = OPEN_FLY_FROM_BLOCK;
    /** "all": any screen closed back to the world; "containers": only container screens (chests, furnaces, …). */
    public String applyTo = APPLY_ALL;
    /**
     * "minimal", "normal" or "cinematic": scales the durations of every effect; choosing one in the editor also sets
     * {@link #effectQuality} and {@link #maxConcurrentEffects}.
     */
    public String preset = "normal";
    /**
     * "low", "medium" or "high": number of pieces and particles; low also has no block collisions, glow layer or texture
     * overlays. Older configs have none: the preset's (which then scaled the counts the same way).
     */
    public String effectQuality;
    /** At most this many effects play at once (openings, closes, particles they left), 1–8. Older configs: the preset's. */
    public Integer maxConcurrentEffects;
    /**
     * What a new effect does when {@link #maxConcurrentEffects} play already: {@link #POLICY_FINISH_OLDEST} — the oldest
     * finish within about 0.2 s; {@link #POLICY_SKIP_NEW} — it does not start (the screen just closes or opens).
     */
    public String concurrentPolicy = POLICY_FINISH_OLDEST;
    /** Calmer effects for everyone sensitive to motion: shorter, no flicker, no sharp moves, fewer particles. */
    public boolean reduceMotion = false;
    /** Screens that never get an effect. Keys: menu type id ("minecraft:furnace") or screen class name. */
    public List<String> disabledScreens = new ArrayList<>();
    /**
     * Optional per-screen exceptions to {@link #effect} and {@link #openEffect}: screen key (as in disabledScreens) →
     * effect name, or an open and a close effect separated by a comma ("fly_from_block,fly").
     */
    public Map<String, String> overrides = new LinkedHashMap<>();
    /**
     * Thematic mode: the close effect depends on the kind of interface ({@link Themes}); off by default. Per-screen
     * {@link #overrides} still come first; interfaces without a rule use {@link #effect}.
     */
    public boolean thematic = false;
    /** The player's changes to the thematic rules: rule id → effect, or "" for the general effect. */
    public Map<String, String> themes = new LinkedHashMap<>();
    /**
     * With Spatial GUI installed: shatter its 3D plane in place and skip its close animation, instead of a flat copy.
     * Without Spatial GUI this does nothing.
     */
    public boolean spatialGuiIntegration = true;
    /**
     * With Iris and an active shader pack: draw shards with vanilla entity render types, which shader packs support.
     * No effect without shaders.
     */
    public boolean shaderCompat = true;
    /**
     * Effects are drawn over the world after it is done (blocks never cut into them) and under the HUD and screens;
     * false: inside the world, hidden by blocks in front of them. Vanilla particles are part of the world either way.
     */
    public boolean alwaysOnTop = true;
    /** Accent colour of the settings editor: "violet", "blue", "teal", "green", "amber" or "rose". */
    public String uiAccent = "violet";
    /** Settings editor: replay the live preview whenever a setting changes. */
    public boolean uiAutoplay = true;
    /**
     * Settings editor: break the real vanilla screens (inventory, chest, furnace, crafting table) in the previews,
     * drawn off-screen; false — the simple painted samples. Needs a world; falls back to the samples on any error.
     */
    public boolean realPreview = true;
    /** Log [ShatterProfile] timings for every close and opening (for finding a stutter; off for players). */
    public boolean profiling = false;
    /** General parameters that differ from the default. */
    public Section general = new Section();
    /** Per effect id: its parameters that differ from the default. */
    public Map<String, Section> effects = new LinkedHashMap<>();

    /** Every parameter value, sanitized; the sections above are only its stored form. */
    private transient Map<Param<?>, Object> values = new HashMap<>();

    /** Stored parameters of one group: id → value. */
    public static final class Section {
        public Map<String, JsonElement> params = new LinkedHashMap<>();
    }

    public static ShatterConfig get() {
        return instance;
    }

    /** Replaces the live config (the editor's Save) and writes it. */
    public static void replace(ShatterConfig config) {
        instance = config;
        save();
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                ShatterConfig loaded = root.isJsonObject() ? fromJson(root.getAsJsonObject()) : null;
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                ShatterGuiClient.LOGGER.error("Failed to read {}, using defaults", PATH, e);
            }
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(instance.toJson(), writer);
            }
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.error("Failed to write {}", PATH, e);
        }
    }

    /** A config from JSON text (older formats included), sanitized; null if it is not a JSON object. */
    public static ShatterConfig parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        ShatterConfig config = root.isJsonObject() ? fromJson(root.getAsJsonObject()) : null;
        if (config != null) {
            config.sanitize();
        }
        return config;
    }

    /** The stored form as JSON text. */
    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    private static ShatterConfig fromJson(JsonObject root) {
        ShatterConfig config = GSON.fromJson(root, ShatterConfig.class);
        if (config == null) {
            return null;
        }
        config.values = new HashMap<>();
        // Older configs: flat keys, moved into the registry unless the new section already has the parameter.
        for (Map.Entry<String, Param<?>> legacy : LEGACY_KEYS.entrySet()) {
            if (root.has(legacy.getKey())) {
                config.values.put(legacy.getValue(), legacy.getValue().fromJson(root.get(legacy.getKey())));
            }
        }
        config.readSection(Params.GENERAL, config.general);
        if (config.effects != null) {
            for (Map.Entry<String, Section> entry : config.effects.entrySet()) {
                config.readSection(entry.getKey(), entry.getValue());
            }
        }
        return config;
    }

    private void readSection(String group, Section section) {
        if (section == null || section.params == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : section.params.entrySet()) {
            Param<?> param = Params.find(group, entry.getKey());
            if (param == null) {
                if (!Params.retired(group, entry.getKey())) {
                    ShatterGuiClient.LOGGER.warn("Unknown parameter {}.{} in {}, ignored", group, entry.getKey(), PATH);
                }
                continue;
            }
            values.put(param, param.fromJson(entry.getValue()));
        }
    }

    /** The stored form: plain fields plus the parameters that differ from their default. */
    private JsonObject toJson() {
        general = new Section();
        effects = new LinkedHashMap<>();
        for (String group : Params.groups()) {
            Section section = new Section();
            for (Param<?> param : Params.group(group)) {
                Object value = values.get(param);
                if (value != null && !value.equals(param.def())) {
                    section.params.put(param.id(), toJson(param, value));
                }
            }
            if (Params.GENERAL.equals(group)) {
                general = section;
            } else if (!section.params.isEmpty()) {
                effects.put(group, section);
            }
        }
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonElement toJson(Param<T> param, Object value) {
        return param.toJson((T) value);
    }

    // --- parameters ---

    @SuppressWarnings("unchecked")
    public <T> T get(Param<T> param) {
        Object value = values.get(param);
        return value != null ? (T) value : param.def();
    }

    /** Sets a parameter from any input; it is sanitized (clamped, validated) first. Returns the stored value. */
    public <T> T set(Param<T> param, Object raw) {
        T value = param.sanitize(raw);
        values.put(param, value);
        return value;
    }

    /** Puts every parameter of a group back to its default. */
    public void resetGroup(String group) {
        for (Param<?> param : Params.group(group)) {
            values.remove(param);
        }
    }

    /** A snapshot for an effect that starts now. */
    public ParamValues values() {
        return new ParamValues(values, Preset.byId(preset), quality(), reduceMotion);
    }

    /** {@link #effectQuality}, or the preset's if it is not set. */
    public Quality quality() {
        Quality quality = Quality.byId(effectQuality);
        return quality != null ? quality : Preset.byId(preset).quality();
    }

    /** {@link #maxConcurrentEffects} within 1–8, or the preset's if it is not set. */
    public int maxConcurrent() {
        int max = maxConcurrentEffects != null ? maxConcurrentEffects : Preset.byId(preset).maxConcurrent();
        return Math.clamp(max, 1, MAX_CONCURRENT_LIMIT);
    }

    /** True for {@link #POLICY_SKIP_NEW}. */
    public boolean skipNewEffects() {
        return POLICY_SKIP_NEW.equals(concurrentPolicy);
    }

    /** Chooses a preset with the quality and number of effects at once that go with it (the editor's preset cards). */
    public void applyPreset(Preset chosen) {
        preset = chosen.id();
        effectQuality = chosen.quality().id();
        maxConcurrentEffects = chosen.maxConcurrent();
    }

    /** An independent copy (for the editor, which edits it and saves it with {@link #replace}). */
    public ShatterConfig copy() {
        ShatterConfig copy = fromJson(toJson());
        copy.sanitize();
        return copy;
    }

    public static boolean isOpenEffect(String name) {
        return OPEN_NONE.equals(name) || OPEN_FLY_FROM_BLOCK.equals(name) || openDataId(name) != null;
    }

    /** The effect id of an "open:<id>" open effect, or null. Whether it exists is checked when a screen opens. */
    public static String openDataId(String name) {
        if (name == null || !name.startsWith(OPEN_DATA_PREFIX)) {
            return null;
        }
        String id = name.substring(OPEN_DATA_PREFIX.length());
        return net.shattergui.fx.FxParser.ID.matcher(id).matches() ? id : null;
    }

    /** The open effect that plays {@code id} when a screen opens, or null if it has none (fly flies out of the block). */
    public static String openValueFor(String id, boolean canOpen, boolean fly) {
        if (fly) {
            return OPEN_FLY_FROM_BLOCK;
        }
        return canOpen ? OPEN_DATA_PREFIX + id : null;
    }

    public boolean containersOnly() {
        return APPLY_CONTAINERS.equals(applyTo);
    }

    private void sanitize() {
        if (values == null) {
            values = new HashMap<>();
        }
        if (effect == null || effect.isBlank()) {
            effect = "glass";
        }
        openEffect = openEffect == null ? OPEN_NONE : openEffect.toLowerCase(Locale.ROOT);
        if (!isOpenEffect(openEffect)) {
            ShatterGuiClient.LOGGER.warn("Unknown openEffect '{}', using '{}'", openEffect, OPEN_NONE);
            openEffect = OPEN_NONE;
        }
        applyTo = applyTo == null ? APPLY_ALL : applyTo.toLowerCase(Locale.ROOT);
        if (!APPLY_ALL.equals(applyTo) && !APPLY_CONTAINERS.equals(applyTo)) {
            ShatterGuiClient.LOGGER.warn("Unknown applyTo '{}', using '{}'", applyTo, APPLY_ALL);
            applyTo = APPLY_ALL;
        }
        preset = Preset.byId(preset).id();
        if (effectQuality != null && Quality.byId(effectQuality) == null) {
            ShatterGuiClient.LOGGER.warn("Unknown effectQuality '{}', using the preset's", effectQuality);
        }
        effectQuality = quality().id();
        maxConcurrentEffects = maxConcurrent();
        if (!POLICY_FINISH_OLDEST.equals(concurrentPolicy) && !POLICY_SKIP_NEW.equals(concurrentPolicy)) {
            if (concurrentPolicy != null) {
                ShatterGuiClient.LOGGER.warn("Unknown concurrentPolicy '{}', using '{}'", concurrentPolicy, POLICY_FINISH_OLDEST);
            }
            concurrentPolicy = POLICY_FINISH_OLDEST;
        }
        uiAccent = Theme.Accent.byId(uiAccent).id();
        if (disabledScreens == null) {
            disabledScreens = new ArrayList<>();
        }
        if (themes == null) {
            themes = new LinkedHashMap<>();
        }
        if (favorites == null) {
            favorites = new ArrayList<>();
        }
        favorites.removeIf(id -> id == null || id.isBlank());
        if (overrides == null) {
            overrides = new LinkedHashMap<>();
        }
        if (general == null) {
            general = new Section();
        }
        if (effects == null) {
            effects = new LinkedHashMap<>();
        }
    }
}
