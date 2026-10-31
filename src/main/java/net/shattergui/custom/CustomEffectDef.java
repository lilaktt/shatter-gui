package net.shattergui.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.shattergui.ShatterGuiClient;
import net.shattergui.params.Param;
import net.shattergui.params.ParamStore;
import net.shattergui.params.Params;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A player's effect: a built-in base effect plus changed parameters, optional extra particles and sounds. Pure data:
 * no code, no class names; particles and sounds are vanilla registry ids. Everything is validated and clamped when
 * read; unknown particles and sounds are skipped with a warning; anything else wrong makes the whole file invalid.
 *
 * <pre>
 * { "schema": 1, "id": "ember_swirl", "name": "Ember swirl", "base": "vortex",
 *   "params":    { "spiralTurns": 3, "vortexDuration": 30 },
 *   "particles": [ { "particle": "minecraft:flame", "density": 0.5, "stage": "during" } ],
 *   "sounds":    [ { "sound": "minecraft:block.fire.extinguish", "volume": 0.5, "pitch": 1.2, "moment": "end" } ] }
 * </pre>
 */
public final class CustomEffectDef implements ParamStore {
    public static final int SCHEMA = 1;
    /** Built-in effects a custom effect can be based on (not on another custom effect). */
    public static final List<String> BASES = List.of("glass", "burn", "dissolve", "fly", "vortex");
    /** Hard limits, also for files from resource packs. */
    public static final int MAX_PARTICLES = 8;
    public static final int MAX_SOUNDS = 8;
    public static final float MAX_DENSITY = 10f;
    public static final int MAX_NAME = 48;
    /** Import: longest accepted code, and largest unpacked JSON. */
    public static final int MAX_CODE_LENGTH = 16_384;
    private static final int MAX_JSON_BYTES = 65_536;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum Stage {
        START, DURING, END;

        static Stage parse(String name) {
            for (Stage stage : values()) {
                if (stage.name().equalsIgnoreCase(name)) {
                    return stage;
                }
            }
            return null;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record ParticleSpec(Identifier particle, SimpleParticleType type, float density, Stage stage) {
    }

    public record SoundSpec(Identifier sound, float volume, float pitch, Stage moment) {
    }

    /** Thrown for a file that cannot be used at all. */
    public static final class InvalidEffectException extends Exception {
        public InvalidEffectException(String message) {
            super(message);
        }
    }

    private String id;
    private String name;
    private final String base;
    private final Map<Param<?>, Object> params = new LinkedHashMap<>();
    private final List<ParticleSpec> particles = new ArrayList<>();
    private final List<SoundSpec> sounds = new ArrayList<>();

    public CustomEffectDef(String id, String name, String base) {
        this.id = id;
        this.name = name;
        this.base = base;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String base() {
        return base;
    }

    public Map<Param<?>, Object> params() {
        return params;
    }

    public List<ParticleSpec> particles() {
        return particles;
    }

    public List<SoundSpec> sounds() {
        return sounds;
    }

    /** Renames (display name only), trimmed and cut to {@link #MAX_NAME}; blank keeps the old name. */
    public void rename(String newName) {
        String clean = clean(newName);
        if (!clean.isEmpty()) {
            name = clean;
        }
    }

    void setId(String newId) {
        id = newId;
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Param<T> param) {
        Object value = params.get(param);
        return value != null ? (T) value : param.def();
    }

    /**
     * Sets an override of the base effect's parameter. Kept even when equal to the registry default: a parameter the
     * effect does not set comes from the player's settings for the base effect, which may differ.
     */
    @Override
    public <T> T set(Param<T> param, Object raw) {
        T value = param.sanitize(raw);
        params.put(param, value);
        return value;
    }

    public boolean has(Param<?> param) {
        return params.containsKey(param);
    }

    /** Drops the override: the parameter comes from the player's settings for the base effect again. */
    public void remove(Param<?> param) {
        params.remove(param);
    }

    public void resetParams() {
        params.clear();
    }

    /** A deep copy with another id and name (cloning, importing under a free id). */
    public CustomEffectDef copy(String newId, String newName) {
        CustomEffectDef copy = new CustomEffectDef(newId, newName, base);
        copy.params.putAll(params);
        copy.particles.addAll(particles);
        copy.sounds.addAll(sounds);
        return copy;
    }

    // --- reading ---

    public static CustomEffectDef parse(String json, String origin) throws InvalidEffectException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new InvalidEffectException("not valid JSON (" + e.getMessage() + ")");
        }
        if (!root.isJsonObject()) {
            throw new InvalidEffectException("not a JSON object");
        }
        return parse(root.getAsJsonObject(), origin);
    }

    public static CustomEffectDef parse(JsonObject json, String origin) throws InvalidEffectException {
        int schema = intOf(json, "schema", -1);
        if (schema != SCHEMA) {
            throw new InvalidEffectException("unsupported schema " + schema + " (expected " + SCHEMA + ")");
        }
        String id = stringOf(json, "id");
        if (!isValidId(id)) {
            throw new InvalidEffectException("id must be 1-32 characters a-z, 0-9 or _, was '" + id + "'");
        }
        String base = stringOf(json, "base");
        if (base == null || !BASES.contains(base.toLowerCase(Locale.ROOT))) {
            throw new InvalidEffectException("base must be one of " + BASES + ", was '" + base + "'");
        }
        String name = clean(stringOf(json, "name"));
        CustomEffectDef def = new CustomEffectDef(id, name.isEmpty() ? id : name, base.toLowerCase(Locale.ROOT));

        if (json.has("params")) {
            if (!json.get("params").isJsonObject()) {
                throw new InvalidEffectException("params must be an object");
            }
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("params").entrySet()) {
                Param<?> param = Params.find(def.base, entry.getKey());
                if (param == null && Params.retired(def.base, entry.getKey())) {
                    continue;
                }
                if (param == null) {
                    ShatterGuiClient.LOGGER.warn("Custom effect {} ({}): unknown parameter '{}' for base {}, skipped",
                            id, origin, entry.getKey(), def.base);
                    continue;
                }
                def.set(param, param.fromJson(entry.getValue()));
            }
        }
        for (JsonObject entry : objects(json, "particles", MAX_PARTICLES, id, origin)) {
            Identifier particleId = Identifier.tryParse(String.valueOf(stringOf(entry, "particle")));
            Stage stage = Stage.parse(String.valueOf(stringOf(entry, "stage")));
            if (particleId == null || stage == null
                    || !(BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId).orElse(null) instanceof SimpleParticleType type)) {
                ShatterGuiClient.LOGGER.warn("Custom effect {} ({}): particle {} skipped (needs a registered particle "
                        + "without options and a stage start/during/end)", id, origin, entry);
                continue;
            }
            def.particles.add(new ParticleSpec(particleId, type, Math.clamp(floatOf(entry, "density", 1f), 0f, MAX_DENSITY),
                    stage));
        }
        for (JsonObject entry : objects(json, "sounds", MAX_SOUNDS, id, origin)) {
            Identifier soundId = Identifier.tryParse(String.valueOf(stringOf(entry, "sound")));
            Stage moment = Stage.parse(String.valueOf(stringOf(entry, "moment")));
            if (soundId == null || moment == null || moment == Stage.DURING
                    || !BuiltInRegistries.SOUND_EVENT.containsKey(soundId)) {
                ShatterGuiClient.LOGGER.warn("Custom effect {} ({}): sound {} skipped (needs a registered sound and a "
                        + "moment start/end)", id, origin, entry);
                continue;
            }
            def.sounds.add(new SoundSpec(soundId, Math.clamp(floatOf(entry, "volume", 1f), 0f, 1f),
                    Math.clamp(floatOf(entry, "pitch", 1f), 0.5f, 2f), moment));
        }
        return def;
    }

    // --- writing ---

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("base", base);
        JsonObject paramsJson = new JsonObject();
        for (Param<?> param : Params.group(base)) {
            Object value = params.get(param);
            if (value != null) {
                paramsJson.add(param.id(), toJson(param, value));
            }
        }
        json.add("params", paramsJson);
        if (!particles.isEmpty()) {
            JsonArray array = new JsonArray();
            for (ParticleSpec spec : particles) {
                JsonObject p = new JsonObject();
                p.addProperty("particle", spec.particle().toString());
                p.addProperty("density", spec.density());
                p.addProperty("stage", spec.stage().id());
                array.add(p);
            }
            json.add("particles", array);
        }
        if (!sounds.isEmpty()) {
            JsonArray array = new JsonArray();
            for (SoundSpec spec : sounds) {
                JsonObject s = new JsonObject();
                s.addProperty("sound", spec.sound().toString());
                s.addProperty("volume", spec.volume());
                s.addProperty("pitch", spec.pitch());
                s.addProperty("moment", spec.moment().id());
                array.add(s);
            }
            json.add("sounds", array);
        }
        return json;
    }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    /** Export code: gzip-compressed JSON in base64. */
    public String toCode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(GSON.toJson(toJson()).getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Import code → effect, with every check of {@link #parse} plus size limits on the code and the unpacked JSON. */
    public static CustomEffectDef fromCode(String code) throws InvalidEffectException {
        if (code == null || code.isBlank()) {
            throw new InvalidEffectException("empty");
        }
        String trimmed = code.trim();
        if (trimmed.length() > MAX_CODE_LENGTH) {
            throw new InvalidEffectException("too long (" + trimmed.length() + " > " + MAX_CODE_LENGTH + " characters)");
        }
        byte[] packed;
        try {
            packed = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw new InvalidEffectException("not a Shatter GUI effect code");
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            byte[] json = in.readNBytes(MAX_JSON_BYTES + 1);
            if (json.length > MAX_JSON_BYTES) {
                throw new InvalidEffectException("unpacked effect too large");
            }
            return parse(new String(json, StandardCharsets.UTF_8), "imported code");
        } catch (IOException e) {
            throw new InvalidEffectException("not a Shatter GUI effect code");
        }
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private static <T> JsonElement toJson(Param<T> param, Object value) {
        return param.toJson((T) value);
    }

    private static String clean(String name) {
        if (name == null) {
            return "";
        }
        String text = name.replaceAll("[\\p{Cntrl}]", "").trim();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }

    private static String stringOf(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int intOf(JsonObject json, String key, int def) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : def;
    }

    private static float floatOf(JsonObject json, String key, float def) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return def;
        }
        float value = e.getAsFloat();
        return Float.isFinite(value) ? value : def;
    }

    private static List<JsonObject> objects(JsonObject json, String key, int max, String id, String origin)
            throws InvalidEffectException {
        List<JsonObject> list = new ArrayList<>();
        if (!json.has(key)) {
            return list;
        }
        if (!json.get(key).isJsonArray()) {
            throw new InvalidEffectException(key + " must be a list");
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (list.size() >= max) {
                ShatterGuiClient.LOGGER.warn("Custom effect {} ({}): more than {} {}, the rest skipped", id, origin, max, key);
                break;
            }
            if (element.isJsonObject()) {
                list.add(element.getAsJsonObject());
            }
        }
        return list;
    }
}
