package net.shattergui.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A JSON value being read, with its path for messages. Reading an object marks its keys as used; {@link #finish()}
 * then warns about every key nobody asked for (unknown fields are ignored, but never silently). Numbers out of range
 * are clamped with a warning; a wrong type is an error.
 */
final class Node {
    private final JsonElement element;
    private final String path;
    private final FxProblems problems;
    private final Set<String> used = new HashSet<>();

    Node(JsonElement element, String path, FxProblems problems) {
        this.element = element;
        this.path = path;
        this.problems = problems;
    }

    String path() {
        return path;
    }

    FxProblems problems() {
        return problems;
    }

    boolean missing() {
        return element == null || element.isJsonNull();
    }

    boolean isObject() {
        return element != null && element.isJsonObject();
    }

    boolean isArray() {
        return element != null && element.isJsonArray();
    }

    boolean isNumber() {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    boolean isString() {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    boolean isBoolean() {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    JsonElement raw() {
        return element;
    }

    private String child(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    /** A field of this object (missing if this is not an object or has no such key). */
    Node get(String key) {
        used.add(key);
        JsonElement value = isObject() ? element.getAsJsonObject().get(key) : null;
        return new Node(value, child(key), problems);
    }

    boolean has(String key) {
        return isObject() && element.getAsJsonObject().has(key);
    }

    /** Requires an object here; an error otherwise. */
    boolean expectObject() {
        if (!isObject()) {
            problems.error(path, "must be an object " + describe());
            return false;
        }
        return true;
    }

    /** Warns about keys of this object that were never read. */
    void finish() {
        if (!isObject()) {
            return;
        }
        for (String key : element.getAsJsonObject().keySet()) {
            if (!used.contains(key)) {
                problems.warn(child(key), "unknown field, ignored");
            }
        }
    }

    // --- primitive fields ---

    float num(String key, float def, float min, float max) {
        Node n = get(key);
        return n.asNum(def, min, max);
    }

    float asNum(float def, float min, float max) {
        if (missing()) {
            return def;
        }
        if (!isNumber()) {
            problems.error(path, "must be a number " + describe());
            return def;
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            problems.error(path, "must be a finite number");
            return def;
        }
        if (value < min || value > max) {
            float clamped = (float) Math.clamp(value, min, max);
            problems.warn(path, "out of range " + fmt(min) + ".." + fmt(max) + ", clamped to " + fmt(clamped));
            return clamped;
        }
        return (float) value;
    }

    int integer(String key, int def, int min, int max) {
        return Math.round(num(key, def, min, max));
    }

    boolean bool(String key, boolean def) {
        Node n = get(key);
        if (n.missing()) {
            return def;
        }
        if (!n.isBoolean()) {
            problems.error(n.path, "must be true or false " + n.describe());
            return def;
        }
        return n.element.getAsBoolean();
    }

    String str(String key, String def) {
        return get(key).asStr(def);
    }

    String asStr(String def) {
        if (missing()) {
            return def;
        }
        if (!isString()) {
            problems.error(path, "must be a string " + describe());
            return def;
        }
        return element.getAsString();
    }

    /** One of the allowed names (case-insensitive); an error listing them otherwise. */
    String choice(String key, String def, List<String> allowed) {
        return get(key).asChoice(def, allowed);
    }

    String asChoice(String def, List<String> allowed) {
        if (missing()) {
            return def;
        }
        String value = asStr(null);
        if (value == null) {
            return def;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!allowed.contains(lower)) {
            problems.error(path, "unknown value \"" + value + "\", expected one of " + allowed);
            return def;
        }
        return lower;
    }

    /** The elements of an array field, at most {@code max} (more: a warning, the rest skipped). */
    List<Node> array(String key, int max) {
        return get(key).asArray(max);
    }

    List<Node> asArray(int max) {
        List<Node> out = new ArrayList<>();
        if (missing()) {
            return out;
        }
        if (!isArray()) {
            problems.error(path, "must be a list " + describe());
            return out;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > max) {
            problems.warn(path, "more than " + max + " entries, the rest is skipped");
        }
        for (int i = 0; i < Math.min(max, array.size()); i++) {
            out.add(new Node(array.get(i), path + "[" + i + "]", problems));
        }
        return out;
    }

    /** An array of exactly {@code size} numbers, each clamped; null (with an error) if it is not one. */
    float[] numbers(int size, float min, float max) {
        if (missing()) {
            return null;
        }
        List<Node> items = asArray(size);
        if (!isArray() || element.getAsJsonArray().size() != size) {
            if (isArray()) {
                problems.error(path, "must have exactly " + size + " numbers");
            }
            return null;
        }
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            if (!items.get(i).isNumber()) {
                problems.error(items.get(i).path, "must be a number " + items.get(i).describe());
                return null;
            }
            out[i] = items.get(i).asNum(0, min, max);
        }
        return out;
    }

    /** A short description of what was found, for error messages. */
    String describe() {
        if (element == null || element.isJsonNull()) {
            return "(missing)";
        }
        if (element.isJsonObject()) {
            return "(found an object)";
        }
        if (element.isJsonArray()) {
            return "(found a list)";
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        String text = primitive.isString() ? "\"" + primitive.getAsString() + "\"" : primitive.toString();
        if (text.length() > 40) {
            text = text.substring(0, 40) + "…";
        }
        return "(found " + text + ")";
    }

    static String fmt(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4g", value);
    }

    static Node of(JsonObject object, FxProblems problems) {
        return new Node(object, "", problems);
    }
}
