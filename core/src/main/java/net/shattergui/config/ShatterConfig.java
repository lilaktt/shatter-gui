package net.shattergui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shattergui.ShatterMod;
import net.shattergui.params.Param;
import net.shattergui.params.ParamStore;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.params.Preset;
import net.shattergui.params.Quality;
import net.shattergui.platform.Platform;
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

public final class ShatterConfig implements ParamStore {
    public static final String APPLY_ALL = "all";
    public static final String APPLY_CONTAINERS = "containers";
    public static final String OPEN_NONE = "none";
    public static final String OPEN_FLY_FROM_BLOCK = "fly_from_block";
    public static final String OPEN_DATA_PREFIX = "open:";
    public static final String POLICY_FINISH_OLDEST = "finishOldest";
    public static final String POLICY_SKIP_NEW = "skipNew";
    public static final int MAX_CONCURRENT_LIMIT = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Platform.get().paths().configDir().resolve("shatter_gui.json");

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

    public boolean enabled = true;
    public String effect = "glass";
    public List<String> favorites = new ArrayList<>();
    public String openEffect = OPEN_FLY_FROM_BLOCK;
    public String applyTo = APPLY_ALL;
    public String preset = "normal";
    public String effectQuality;
    public Integer maxConcurrentEffects;
    public String concurrentPolicy = POLICY_FINISH_OLDEST;
    public boolean reduceMotion = false;
    public List<String> disabledScreens = new ArrayList<>();
    public Map<String, String> overrides = new LinkedHashMap<>();
    public boolean thematic = false;
    public Map<String, String> themes = new LinkedHashMap<>();
    public boolean spatialGuiIntegration = true;
    public boolean shaderCompat = true;
    public boolean alwaysOnTop = true;
    public boolean linkStatusCheck = true;
    public String uiAccent = "violet";
    public boolean uiAutoplay = true;
    public boolean realPreview = true;
    public boolean profiling = false;
    public Section general = new Section();
    public Map<String, Section> effects = new LinkedHashMap<>();

    private transient Map<Param<?>, Object> values = new HashMap<>();

    public static final class Section {
        public Map<String, JsonElement> params = new LinkedHashMap<>();
    }

    public static ShatterConfig get() {
        return instance;
    }

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
                ShatterMod.LOGGER.error("Failed to read {}, using defaults", PATH, e);
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
            ShatterMod.LOGGER.error("Failed to write {}", PATH, e);
        }
    }

    public static ShatterConfig parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        ShatterConfig config = root.isJsonObject() ? fromJson(root.getAsJsonObject()) : null;
        if (config != null) {
            config.sanitize();
        }
        return config;
    }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    private static ShatterConfig fromJson(JsonObject root) {
        ShatterConfig config = GSON.fromJson(root, ShatterConfig.class);
        if (config == null) {
            return null;
        }
        config.values = new HashMap<>();
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
                    ShatterMod.LOGGER.warn("Unknown parameter {}.{} in {}, ignored", group, entry.getKey(), PATH);
                }
                continue;
            }
            values.put(param, param.fromJson(entry.getValue()));
        }
    }

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

    @SuppressWarnings("unchecked")
    public <T> T get(Param<T> param) {
        Object value = values.get(param);
        return value != null ? (T) value : param.def();
    }

    public <T> T set(Param<T> param, Object raw) {
        T value = param.sanitize(raw);
        values.put(param, value);
        return value;
    }

    public void resetGroup(String group) {
        for (Param<?> param : Params.group(group)) {
            values.remove(param);
        }
    }

    public ParamValues values() {
        return new ParamValues(values, Preset.byId(preset), quality(), reduceMotion);
    }

    public Quality quality() {
        Quality quality = Quality.byId(effectQuality);
        return quality != null ? quality : Preset.byId(preset).quality();
    }

    public int maxConcurrent() {
        int max = maxConcurrentEffects != null ? maxConcurrentEffects : Preset.byId(preset).maxConcurrent();
        return Math.clamp(max, 1, MAX_CONCURRENT_LIMIT);
    }

    public boolean skipNewEffects() {
        return POLICY_SKIP_NEW.equals(concurrentPolicy);
    }

    public void applyPreset(Preset chosen) {
        preset = chosen.id();
        effectQuality = chosen.quality().id();
        maxConcurrentEffects = chosen.maxConcurrent();
    }

    public ShatterConfig copy() {
        ShatterConfig copy = fromJson(toJson());
        copy.sanitize();
        return copy;
    }

    public static boolean isOpenEffect(String name) {
        return OPEN_NONE.equals(name) || OPEN_FLY_FROM_BLOCK.equals(name) || openDataId(name) != null;
    }

    public static String openDataId(String name) {
        if (name == null || !name.startsWith(OPEN_DATA_PREFIX)) {
            return null;
        }
        String id = name.substring(OPEN_DATA_PREFIX.length());
        return net.shattergui.fx.FxParser.ID.matcher(id).matches() ? id : null;
    }

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
            ShatterMod.LOGGER.warn("Unknown openEffect '{}', using '{}'", openEffect, OPEN_NONE);
            openEffect = OPEN_NONE;
        }
        applyTo = applyTo == null ? APPLY_ALL : applyTo.toLowerCase(Locale.ROOT);
        if (!APPLY_ALL.equals(applyTo) && !APPLY_CONTAINERS.equals(applyTo)) {
            ShatterMod.LOGGER.warn("Unknown applyTo '{}', using '{}'", applyTo, APPLY_ALL);
            applyTo = APPLY_ALL;
        }
        preset = Preset.byId(preset).id();
        if (effectQuality != null && Quality.byId(effectQuality) == null) {
            ShatterMod.LOGGER.warn("Unknown effectQuality '{}', using the preset's", effectQuality);
        }
        effectQuality = quality().id();
        maxConcurrentEffects = maxConcurrent();
        if (!POLICY_FINISH_OLDEST.equals(concurrentPolicy) && !POLICY_SKIP_NEW.equals(concurrentPolicy)) {
            if (concurrentPolicy != null) {
                ShatterMod.LOGGER.warn("Unknown concurrentPolicy '{}', using '{}'", concurrentPolicy, POLICY_FINISH_OLDEST);
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
