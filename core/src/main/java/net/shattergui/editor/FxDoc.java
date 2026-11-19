package net.shattergui.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class FxDoc {
    private static final int MAX_HISTORY = 120;
    private static final long MERGE_MILLIS = 900;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonObject root;
    private String text;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String mergeKey;
    private long mergeTime;
    private int version;
    private String savedText;
    private String loadedText = "";
    private FxParser.Result parsed;
    private String parsedText;

    public FxDoc(String json) {
        load(json);
        savedText = text;
    }

    public void load(String json) {
        root = readObject(json);
        text = GSON.toJson(root);
        loadedText = text;
        undo.clear();
        redo.clear();
        mergeKey = null;
        savedText = text;
        version++;
    }

    private static JsonObject readObject(String json) {
        try {
            JsonElement element = JsonParser.parseString(json == null ? "" : json);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (RuntimeException e) {
        }
        JsonObject empty = new JsonObject();
        empty.addProperty("schema", 2);
        return empty;
    }

    public JsonObject root() {
        return root;
    }

    public String json() {
        return text;
    }

    public int version() {
        return version;
    }

    public boolean dirty() {
        return !text.equals(savedText);
    }

    public boolean changedSinceLoad() {
        return !text.equals(loadedText);
    }

    public void markSaved() {
        savedText = text;
    }

    public void edit(String key, Consumer<JsonObject> change) {
        String before = text;
        JsonObject copy = root.deepCopy();
        try {
            change.accept(copy);
        } catch (RuntimeException e) {
            return;
        }
        String after = GSON.toJson(copy);
        if (after.equals(before)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean merge = key != null && key.equals(mergeKey) && now - mergeTime < MERGE_MILLIS && !undo.isEmpty();
        if (!merge) {
            undo.push(before);
            while (undo.size() > MAX_HISTORY) {
                undo.removeLast();
            }
        }
        redo.clear();
        mergeKey = key;
        mergeTime = now;
        root = copy;
        text = after;
        version++;
    }

    public void breakMerge() {
        mergeKey = null;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(text);
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(text);
        restore(redo.pop());
        return true;
    }

    private void restore(String snapshot) {
        root = readObject(snapshot);
        text = GSON.toJson(root);
        mergeKey = null;
        version++;
    }

    public void set(String path, JsonElement value) {
        edit(path, r -> put(r, path, value));
    }

    public void set(String path, double value) {
        set(path, number(value));
    }

    public void set(String path, String value) {
        set(path, new JsonPrimitive(value));
    }

    public void set(String path, boolean value) {
        set(path, new JsonPrimitive(value));
    }

    public void remove(String path) {
        edit(null, r -> delete(r, path));
    }

    public static JsonPrimitive number(double value) {
        if (!Double.isFinite(value)) {
            value = 0;
        }
        double rounded = Math.round(value * 10000.0) / 10000.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e9) {
            return new JsonPrimitive((long) rounded);
        }
        return new JsonPrimitive(rounded);
    }

    public JsonElement get(String path) {
        return get(root, path);
    }

    public float num(String path, float def) {
        JsonElement e = get(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : def;
    }

    public String str(String path, String def) {
        JsonElement e = get(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : def;
    }

    public boolean bool(String path, boolean def) {
        JsonElement e = get(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }

    public boolean has(String path) {
        JsonElement e = get(path);
        return e != null && !e.isJsonNull();
    }

    public int size(String path) {
        JsonElement e = get(path);
        return e != null && e.isJsonArray() ? e.getAsJsonArray().size() : 0;
    }

    public int append(String path, JsonElement item) {
        int index = size(path);
        edit(null, r -> {
            JsonElement list = get(r, path);
            if (list == null || !list.isJsonArray()) {
                put(r, path, new JsonArray());
                list = get(r, path);
            }
            list.getAsJsonArray().add(item);
        });
        return index;
    }

    public void move(String path, int from, int to) {
        if (from == to) {
            return;
        }
        edit(null, r -> {
            JsonElement list = get(r, path);
            if (list == null || !list.isJsonArray()) {
                return;
            }
            JsonArray array = list.getAsJsonArray();
            if (from < 0 || from >= array.size() || to < 0 || to >= array.size()) {
                return;
            }
            JsonElement item = array.remove(from);
            List<JsonElement> items = new ArrayList<>();
            array.forEach(items::add);
            items.add(to, item);
            while (!array.isEmpty()) {
                array.remove(0);
            }
            items.forEach(array::add);
        });
    }

    public int duplicate(String path, int index) {
        JsonElement list = get(path);
        if (list == null || !list.isJsonArray() || index < 0 || index >= list.getAsJsonArray().size()) {
            return -1;
        }
        edit(null, r -> {
            JsonArray array = get(r, path).getAsJsonArray();
            List<JsonElement> items = new ArrayList<>();
            array.forEach(items::add);
            items.add(index + 1, items.get(index).deepCopy());
            while (!array.isEmpty()) {
                array.remove(0);
            }
            items.forEach(array::add);
        });
        return index + 1;
    }

    public FxParser.Result result() {
        if (parsed == null || !text.equals(parsedText)) {
            parsedText = text;
            try {
                parsed = FxParser.parse(text);
            } catch (RuntimeException e) {
                FxProblems problems = new FxProblems();
                problems.error("", "could not be read: " + e);
                parsed = new FxParser.Result(null, problems);
            }
        }
        return parsed;
    }

    public List<FxProblems.Problem> problems(String path) {
        return under(result().problems().all(), path);
    }

    public static List<FxProblems.Problem> under(List<FxProblems.Problem> all, String path) {
        List<FxProblems.Problem> out = new ArrayList<>();
        for (FxProblems.Problem p : all) {
            if (isUnder(p.path(), path)) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || prefix.isEmpty() || path.startsWith(prefix + ".") || path.startsWith(prefix + "[");
    }

    static List<Object> tokens(String path) {
        List<Object> out = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.') {
                if (!key.isEmpty()) {
                    out.add(key.toString());
                    key.setLength(0);
                }
            } else if (c == '[') {
                if (!key.isEmpty()) {
                    out.add(key.toString());
                    key.setLength(0);
                }
                int close = path.indexOf(']', i);
                if (close < 0) {
                    throw new IllegalArgumentException("bad path " + path);
                }
                out.add(Integer.parseInt(path.substring(i + 1, close)));
                i = close;
            } else {
                key.append(c);
            }
        }
        if (!key.isEmpty()) {
            out.add(key.toString());
        }
        return out;
    }

    public static JsonElement get(JsonElement root, String path) {
        JsonElement current = root;
        for (Object token : tokens(path)) {
            if (current == null) {
                return null;
            }
            if (token instanceof String key) {
                current = current.isJsonObject() ? current.getAsJsonObject().get(key) : null;
            } else {
                int index = (Integer) token;
                current = current.isJsonArray() && index >= 0 && index < current.getAsJsonArray().size()
                        ? current.getAsJsonArray().get(index) : null;
            }
        }
        return current;
    }

    public static void put(JsonObject root, String path, JsonElement value) {
        List<Object> tokens = tokens(path);
        JsonElement current = root;
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            boolean last = i == tokens.size() - 1;
            Object next = last ? null : tokens.get(i + 1);
            if (token instanceof String key) {
                JsonObject obj = current.getAsJsonObject();
                if (last) {
                    obj.add(key, value == null ? JsonNull.INSTANCE : value);
                    return;
                }
                JsonElement child = obj.get(key);
                boolean wantArray = next instanceof Integer;
                if (child == null || wantArray && !child.isJsonArray() || !wantArray && !child.isJsonObject()) {
                    child = wantArray ? new JsonArray() : new JsonObject();
                    obj.add(key, child);
                }
                current = child;
            } else {
                JsonArray array = current.getAsJsonArray();
                int index = (Integer) token;
                if (index < 0 || index > array.size()) {
                    throw new IndexOutOfBoundsException(path);
                }
                if (last) {
                    if (index == array.size()) {
                        array.add(value);
                    } else {
                        array.set(index, value);
                    }
                    return;
                }
                boolean wantArray = next instanceof Integer;
                JsonElement child = index < array.size() ? array.get(index) : null;
                if (child == null || wantArray && !child.isJsonArray() || !wantArray && !child.isJsonObject()) {
                    child = wantArray ? new JsonArray() : new JsonObject();
                    if (index == array.size()) {
                        array.add(child);
                    } else {
                        array.set(index, child);
                    }
                }
                current = child;
            }
        }
    }

    public static void delete(JsonObject root, String path) {
        List<Object> tokens = tokens(path);
        if (tokens.isEmpty()) {
            return;
        }
        StringBuilder parent = new StringBuilder();
        for (int i = 0; i < tokens.size() - 1; i++) {
            Object t = tokens.get(i);
            parent.append(t instanceof Integer ? "[" + t + "]" : (parent.isEmpty() ? "" : ".") + t);
        }
        JsonElement container = parent.isEmpty() ? root : get(root, parent.toString());
        Object last = tokens.getLast();
        if (container == null) {
            return;
        }
        if (last instanceof String key && container.isJsonObject()) {
            container.getAsJsonObject().remove(key);
        } else if (last instanceof Integer index && container.isJsonArray() && index >= 0
                && index < container.getAsJsonArray().size()) {
            container.getAsJsonArray().remove((int) index);
        }
    }

    public static String pretty(JsonElement element) {
        return GSON.toJson(element);
    }
}
