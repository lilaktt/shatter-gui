package net.shattergui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.shattergui.fx.Easing;
import net.shattergui.fx.FxSchema;
import net.shattergui.fx.Value;

/**
 * The five ways a number can be written (a number, a random range, an easing curve, a Bézier, keyframes) and the
 * conversions between them that keep the value's shape as well as they can. Pure JSON work, no game classes.
 */
public final class ValueForms {
    public enum Form {
        NUMBER, RANDOM, CURVE, BEZIER, KEYS
    }

    private ValueForms() {
    }

    public static Form formOf(JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return Form.NUMBER;
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("keys")) {
            return Form.KEYS;
        }
        if (o.has("bezier")) {
            return Form.BEZIER;
        }
        if (o.has("curve")) {
            return Form.CURVE;
        }
        return Form.RANDOM;
    }

    /** The value as the engine reads it (without per-piece jitter, for drawing); {@code def} if missing. */
    public static Value read(JsonElement e, JsonElement def, FxSchema.Field field) {
        JsonElement source = e == null || e.isJsonNull() ? def : e;
        if (source != null && source.isJsonObject() && source.getAsJsonObject().has("jitter")) {
            source = source.deepCopy();
            source.getAsJsonObject().remove("jitter");
        }
        return Value.fromJson(source, field.def(), field.min(), field.max());
    }

    /** Converts to another form, keeping start, end and jitter where it can. */
    public static JsonElement convert(JsonElement e, JsonElement def, FxSchema.Field field, Form to) {
        Value v = read(e, def, field);
        float a = v.at(0, 0.5f);
        float b = v.at(1, 0.5f);
        float mid = v.at(0.5f, 0.5f);
        Form from = formOf(e == null || e.isJsonNull() ? def : e);
        float jitter = 0;
        JsonElement source = e == null || e.isJsonNull() ? def : e;
        if (source != null && source.isJsonObject() && source.getAsJsonObject().has("jitter")) {
            jitter = source.getAsJsonObject().get("jitter").getAsFloat();
        }
        JsonElement out;
        switch (to) {
            case NUMBER -> out = FxDoc.number(mid);
            case RANDOM -> {
                float lo = from == Form.RANDOM ? v.at(0, 0) : clamp(field, mid * 0.75f);
                float hi = from == Form.RANDOM ? v.at(0, 1) : clamp(field, mid * 1.25f);
                JsonObject o = new JsonObject();
                JsonArray range = new JsonArray();
                range.add(FxDoc.number(Math.min(lo, hi)));
                range.add(FxDoc.number(Math.max(lo, hi)));
                o.add("random", range);
                out = o;
            }
            case CURVE -> {
                JsonObject o = new JsonObject();
                o.addProperty("curve", Easing.EASE_IN_OUT.configName());
                o.add("from", FxDoc.number(a));
                o.add("to", FxDoc.number(from == Form.NUMBER || from == Form.RANDOM ? clamp(field, field.def()) : b));
                out = o;
            }
            case BEZIER -> {
                JsonObject o = new JsonObject();
                JsonArray p = new JsonArray();
                for (float f : new float[]{0.25f, 0.1f, 0.25f, 1f}) {
                    p.add(FxDoc.number(f));
                }
                o.add("bezier", p);
                o.add("from", FxDoc.number(a));
                o.add("to", FxDoc.number(from == Form.NUMBER || from == Form.RANDOM ? clamp(field, field.def()) : b));
                out = o;
            }
            default -> {
                JsonObject o = new JsonObject();
                JsonArray keys = new JsonArray();
                float[] times = from == Form.CURVE || from == Form.BEZIER ? new float[]{0, 0.25f, 0.5f, 0.75f, 1}
                        : new float[]{0, 1};
                for (float t : times) {
                    keys.add(pair(t, from == Form.RANDOM ? mid : v.at(t, 0.5f)));
                }
                o.add("keys", keys);
                if (from == Form.KEYS && source != null && source.isJsonObject() && source.getAsJsonObject().has("ease")) {
                    o.add("ease", source.getAsJsonObject().get("ease"));
                }
                out = o;
            }
        }
        if (jitter > 0 && out.isJsonObject()) {
            out.getAsJsonObject().add("jitter", FxDoc.number(jitter));
        }
        return out;
    }

    private static float clamp(FxSchema.Field field, float v) {
        return Math.clamp(v, field.min(), field.max());
    }

    public static JsonArray pair(float t, float v) {
        JsonArray pair = new JsonArray();
        pair.add(FxDoc.number(t));
        pair.add(FxDoc.number(v));
        return pair;
    }

    /** Keyframes [time, value] of a keys object (empty if it is not one). */
    public static float[][] keys(JsonElement e) {
        if (e == null || !e.isJsonObject() || !e.getAsJsonObject().has("keys") || !e.getAsJsonObject().get("keys").isJsonArray()) {
            return new float[0][];
        }
        JsonArray array = e.getAsJsonObject().getAsJsonArray("keys");
        float[][] out = new float[array.size()][];
        for (int i = 0; i < array.size(); i++) {
            JsonElement p = array.get(i);
            if (p.isJsonArray() && p.getAsJsonArray().size() == 2 && p.getAsJsonArray().get(0).isJsonPrimitive()
                    && p.getAsJsonArray().get(1).isJsonPrimitive()) {
                try {
                    out[i] = new float[]{p.getAsJsonArray().get(0).getAsFloat(), p.getAsJsonArray().get(1).getAsFloat()};
                } catch (RuntimeException ex) {
                    out[i] = new float[]{0, 0};
                }
            } else {
                out[i] = new float[]{0, 0};
            }
        }
        return out;
    }

    /** Writes keyframes back (sorted by time). */
    public static JsonArray keysJson(float[][] keys) {
        float[][] sorted = keys.clone();
        java.util.Arrays.sort(sorted, (p, q) -> Float.compare(p[0], q[0]));
        JsonArray array = new JsonArray();
        for (float[] k : sorted) {
            array.add(pair(k[0], k[1]));
        }
        return array;
    }

    /** The 4 Bézier numbers of a bezier object (a default if odd). */
    public static float[] bezier(JsonElement e) {
        try {
            JsonArray p = e.getAsJsonObject().getAsJsonArray("bezier");
            return new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), p.get(3).getAsFloat()};
        } catch (RuntimeException ex) {
            return new float[]{0.25f, 0.1f, 0.25f, 1f};
        }
    }

    public static JsonArray bezierJson(float[] p) {
        JsonArray a = new JsonArray();
        for (float f : p) {
            a.add(FxDoc.number(f));
        }
        return a;
    }

    /** A number field of an object, or the default. */
    public static float num(JsonElement e, String key, float def) {
        try {
            JsonElement v = e.getAsJsonObject().get(key);
            return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsFloat() : def;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static String str(JsonElement e, String key, String def) {
        try {
            JsonElement v = e.getAsJsonObject().get(key);
            return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() ? v.getAsString() : def;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    /** A random range [lo, hi] of a random/value object. */
    public static float[] range(JsonElement e, float def) {
        try {
            JsonObject o = e.getAsJsonObject();
            if (o.has("value")) {
                float v = o.get("value").getAsFloat();
                return new float[]{v, v};
            }
            JsonArray r = o.getAsJsonArray("random");
            return new float[]{r.get(0).getAsFloat(), r.get(1).getAsFloat()};
        } catch (RuntimeException ex) {
            return new float[]{def, def};
        }
    }

    static JsonPrimitive text(String s) {
        return new JsonPrimitive(s);
    }
}
