package net.shattergui.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads a schema-2 effect (see {@code docs/EFFECT_FORMAT.md}). Pure Java (Gson only), so it is unit-tested without the
 * game. Problems carry the path of the field; an error rejects the effect, a warning clamps or skips one thing.
 * Particle and sound ids are only checked for their form here; whether the game knows them is checked when the effect
 * is loaded.
 */
public final class FxParser {
    public record Result(FxDef def, FxProblems problems) {
        public boolean ok() {
            return def != null;
        }
    }

    public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    public static final Pattern RESOURCE_ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    public static final Pattern TEXTURE_NAME = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]{1,64}");
    private static final int MAX_NAME = 48;
    private static final int MAX_DESCRIPTION = 200;
    public static final int MAX_LABEL = 32;

    private FxParser() {
    }

    public static Result parse(String json) {
        FxProblems problems = new FxProblems();
        if (json.getBytes(StandardCharsets.UTF_8).length > FxLimits.MAX_JSON_BYTES) {
            problems.error("", "file too large (over " + FxLimits.MAX_JSON_BYTES / 1024 + " KB)");
            return new Result(null, problems);
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            problems.error("", "not valid JSON: " + message.replace("com.google.gson.stream.MalformedJsonException: ", ""));
            return new Result(null, problems);
        }
        if (root == null || !root.isJsonObject()) {
            problems.error("", "the effect must be a JSON object");
            return new Result(null, problems);
        }
        Node node = Node.of(root.getAsJsonObject(), problems);
        FxDef def = read(node, json);
        JsonElement reduced = root.getAsJsonObject().get("reduced");
        if (reduced != null && def != null) {
            if (!reduced.isJsonObject()) {
                problems.error("reduced", "must be an object with the fields to change for reduce motion");
            } else {
                // The same effect with these fields replaced (objects merged, everything else swapped).
                JsonObject merged = merge(root.getAsJsonObject().deepCopy(), reduced.getAsJsonObject());
                merged.remove("reduced");
                FxProblems sub = new FxProblems();
                FxDef calm = read(Node.of(merged, sub), merged.toString());
                for (FxProblems.Problem p : sub.all()) {
                    String path = "reduced" + (p.path().isEmpty() ? "" : "." + p.path());
                    if (p.error()) {
                        problems.error(path, p.message());
                    } else if (!reduced.getAsJsonObject().has(p.path().split("[.\\[]")[0])) {
                        // Warnings of fields it did not change were already reported for the effect itself.
                        continue;
                    } else {
                        problems.warn(path, p.message());
                    }
                }
                def.reduced = sub.hasErrors() ? null : calm;
            }
        }
        return new Result(problems.hasErrors() ? null : def, problems);
    }

    /** {@code over} merged into {@code base}: objects field by field, anything else replaced. */
    private static JsonObject merge(JsonObject base, JsonObject over) {
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            JsonElement old = base.get(entry.getKey());
            if (old != null && old.isJsonObject() && entry.getValue().isJsonObject()) {
                merge(old.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                base.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return base;
    }

    /** The "schema" of a JSON text (1 if missing or unreadable), to pick the reader. */
    public static int schemaOf(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonObject() && root.getAsJsonObject().has("schema")
                    && root.getAsJsonObject().get("schema").isJsonPrimitive()) {
                return root.getAsJsonObject().get("schema").getAsInt();
            }
        } catch (RuntimeException e) {
            // Unreadable: the schema-1 reader reports it.
        }
        return 1;
    }

    private static FxDef read(Node root, String json) {
        FxProblems problems = root.problems();
        Node schema = root.get("schema");
        if (!schema.isNumber() || schema.raw().getAsInt() != FxLimits.SCHEMA) {
            problems.error("schema", "must be " + FxLimits.SCHEMA + " " + schema.describe());
        }
        String id = root.str("id", null);
        if (id == null) {
            problems.error("id", "is required");
        } else if (!ID.matcher(id).matches()) {
            problems.error("id", "must be 1-32 characters a-z, 0-9 or _ (\"" + id + "\")");
        }
        String name = clean(root.str("name", id == null ? "" : id), MAX_NAME);
        String description = clean(root.str("description", ""), MAX_DESCRIPTION);
        int duration = root.integer("duration", 40, FxLimits.MIN_DURATION, FxLimits.MAX_DURATION);
        boolean openable = root.bool("open", false);
        // Read (checked) by parse(): only marked as known here.
        root.get("reduced");
        Map<String, FxDef.TextureRole> textures = new LinkedHashMap<>();

        FxDef.Fracture fracture = readFracture(root.get("fracture"));
        FxDef.Trigger trigger = readTrigger(root.get("trigger"), textures);

        List<FxDef.Layer> layers = new ArrayList<>();
        if (!root.has("layers")) {
            problems.warn("layers", "no layers: the pieces stay where they are and only change their look");
        }
        for (Node layer : root.array("layers", FxLimits.MAX_LAYERS)) {
            FxDef.Layer read = readLayer(layer);
            if (read != null) {
                layers.add(read);
            }
        }
        FxDef.Appearance appearance = readAppearance(root.get("appearance"), textures);
        List<FxDef.Emitter> emitters = new ArrayList<>();
        for (Node emitter : root.array("emitters", FxLimits.MAX_EMITTERS)) {
            FxDef.Emitter read = readEmitter(emitter, textures);
            if (read != null) {
                emitters.add(read);
            }
        }
        List<FxDef.Sound> sounds = new ArrayList<>();
        for (Node sound : root.array("sounds", FxLimits.MAX_SOUNDS)) {
            FxDef.Sound read = readSound(sound);
            if (read != null) {
                sounds.add(read);
            }
        }
        FxDef.Edge edge = null;
        Node modifiers = root.get("modifiers");
        if (!modifiers.missing() && modifiers.expectObject()) {
            Node edgeNode = modifiers.get("edge");
            if (!edgeNode.missing()) {
                edge = readEdge(edgeNode);
            }
            modifiers.finish();
        }
        if (textures.size() > FxLimits.MAX_TEXTURES) {
            problems.error("textures", "more than " + FxLimits.MAX_TEXTURES + " different textures (" + textures.size() + ")");
        }
        root.finish();
        return new FxDef(id, name.isEmpty() ? String.valueOf(id) : name, description, duration, fracture, trigger,
                List.copyOf(layers), appearance, List.copyOf(emitters), List.copyOf(sounds), edge, textures, json, openable);
    }

    // --- sections ---

    private static FxDef.Fracture readFracture(Node n) {
        if (n.missing()) {
            return new FxDef.Fracture("grid", 60, 0, 0, 8, 0.35f, false, false, 1, 3, FxDef.Point.CENTER, null);
        }
        if (!n.expectObject()) {
            return new FxDef.Fracture("grid", 60, 0, 0, 8, 0.35f, false, false, 1, 3, FxDef.Point.CENTER, null);
        }
        String mode = n.choice("mode", "grid", FxDef.Fracture.MODES);
        int maxCount = "voronoi".equals(mode) ? FxLimits.MAX_VORONOI_CELLS : FxLimits.MAX_PIECES;
        int count = n.integer("count", "radial".equals(mode) ? 12 : 60, 1, maxCount);
        int cols = n.integer("cols", 0, 0, 64);
        int rows = n.integer("rows", 0, 0, 64);
        float size = n.num("size", 8, 2, 64);
        float jitter = n.num("jitter", 0.35f, 0, 1);
        boolean split = n.bool("split", false);
        boolean vertical = "vertical".equals(n.choice("direction", "horizontal", List.of("horizontal", "vertical")));
        int segments = n.integer("segments", 1, 1, 32);
        int rings = n.integer("rings", 3, 1, 12);
        FxDef.Point center = readPoint(n.get("center"), FxDef.Point.CENTER);
        float[] region = null;
        Node regionNode = n.get("region");
        if (!regionNode.missing()) {
            region = regionNode.numbers(4, 0, 1);
            if (region != null && (region[2] - region[0] < 0.02f || region[3] - region[1] < 0.02f)) {
                n.problems().error(regionNode.path(), "must be [u0, v0, u1, v1] with u1 > u0 and v1 > v0");
                region = null;
            }
        }
        n.finish();
        return new FxDef.Fracture(mode, count, cols, rows, size, jitter, split, vertical, segments, rings, center, region);
    }

    private static FxDef.Trigger readTrigger(Node n, Map<String, FxDef.TextureRole> textures) {
        if (n.missing() || !n.expectObject()) {
            return new FxDef.Trigger("wave", 0, -1, FxDef.Point.CENTER, 4, null, false, 0, 0.5f, Easing.LINEAR);
        }
        String type = n.choice("type", "wave", FxDef.Trigger.TYPES);
        float[] direction = n.get("direction").numbers(2, -1, 1);
        if (direction == null) {
            direction = new float[]{0, -1};
        }
        if (Math.abs(direction[0]) + Math.abs(direction[1]) < 1e-3f) {
            n.problems().error(n.path() + ".direction", "must not be [0, 0]");
            direction = new float[]{0, -1};
        }
        FxDef.Point from = readPoint(n.get("from"), FxDef.Point.CENTER);
        float scale = n.num("scale", 4, 0.5f, 32);
        String texture = null;
        if ("mask".equals(type)) {
            texture = textureRef(n.get("texture"), FxDef.TextureRole.MASK, textures, true);
        } else if (n.has("texture")) {
            n.get("texture");
            n.problems().warn(n.path() + ".texture", "only used by type \"mask\", ignored");
        }
        boolean invert = n.bool("invert", false);
        float noise = n.num("noise", 0, 0, 1);
        float spread = n.num("spread", 0.5f, 0, 0.95f);
        Easing curve = easing(n.get("curve"), Easing.LINEAR);
        n.finish();
        return new FxDef.Trigger(type, direction[0], direction[1], from, scale, texture, invert, noise, spread, curve);
    }

    private static FxDef.Layer readLayer(Node n) {
        if (!n.expectObject()) {
            return null;
        }
        String moduleName = n.str("module", null);
        FxDef.Module module = moduleName == null ? null : FxDef.Module.byName(moduleName);
        if (moduleName == null) {
            n.problems().error(n.path() + ".module", "is required, one of " + FxDef.Module.NAMES);
        } else if (module == null) {
            n.problems().error(n.path() + ".module", "unknown module \"" + moduleName + "\", expected one of " + FxDef.Module.NAMES);
        }
        readLabel(n);
        boolean enabled = n.bool("enabled", true);
        FxDef.Target target = readTarget(n.get("target"));
        float from = n.num("from", 0, 0, 1);
        float to = n.num("to", 1, 0, 1);
        if (to < from) {
            n.problems().error(n.path(), "\"to\" (" + Node.fmt(to) + ") is before \"from\" (" + Node.fmt(from) + ")");
        }
        boolean global = "global".equals(n.choice("time", "local", List.of("local", "global")));
        Map<String, Value> values = new LinkedHashMap<>();
        FxDef.Point point = FxDef.Point.CENTER;
        float[][] path = null;
        boolean world = true;
        boolean once = true;
        String rest = "lie";
        float axis = Float.NaN;
        float[] pivot = null;
        Node params = n.get("params");
        if (!params.missing() && !params.expectObject()) {
            params = new Node(null, params.path(), n.problems());
        }
        if (module != null) {
            for (FxSchema.Field p : FxSchema.MODULE_PARAMS.get(module)) {
                String param = FxSchema.paramName(p);
                Value value;
                if (module == FxDef.Module.FOLLOW_PATH && !params.has(param)) {
                    value = new Value.Curve(Easing.SMOOTH, 0, 1, 0, 1, 0);
                } else {
                    value = Value.read(params.get(param), p.def(), p.min(), p.max());
                }
                values.put(param, value);
            }
            switch (module) {
                case LAUNCH -> {
                    point = readPoint(params.get("from"), FxDef.Point.CENTER);
                    once = params.bool("once", true);
                }
                case ATTRACT -> point = readPoint(params.get("to"), FxDef.Point.CENTER);
                case GRAVITY -> world = params.bool("world", true);
                case FOLLOW_PATH -> path = readPath(params.get("points"));
                case COLLIDE -> rest = params.choice("rest", "lie", FxDef.Layer.RESTS);
                case SPIN -> {
                    if (params.has("axis")) {
                        FxSchema.Field f = FxSchema.field("spin.axis");
                        axis = params.num("axis", f.def(), f.min(), f.max());
                    }
                    // An exact tumble angle over the window instead of a speed: ends edge-on exactly.
                    if (params.has("turn")) {
                        FxSchema.Field f = FxSchema.field("spin.turn");
                        values.put("turn", Value.read(params.get("turn"), f.def(), f.min(), f.max()));
                    }
                    if (params.has("pivot")) {
                        Node at = params.get("pivot");
                        pivot = at.numbers(2, -1, 2);
                        if (pivot == null) {
                            n.problems().error(at.path(), "must be [u, v] (0..1 of the interface from the left and the top) "
                                    + at.describe());
                        }
                    }
                }
                default -> {
                }
            }
            params.finish();
        }
        n.finish();
        if (module == null || !enabled) {
            return null;
        }
        return new FxDef.Layer(target, module, from, to, global, values, point, path, world, once, rest, axis,
                pivot);
    }

    private static float[][] readPath(Node n) {
        if (n.missing()) {
            n.problems().error(n.path(), "follow-path needs \"points\": a list of [x, y] or [x, y, z] offsets");
            return new float[][]{{0, 0, 0}};
        }
        List<Node> items = n.asArray(FxLimits.MAX_PATH_POINTS);
        if (items.isEmpty()) {
            n.problems().error(n.path(), "needs at least one point");
            return new float[][]{{0, 0, 0}};
        }
        float[][] out = new float[items.size()][];
        for (int i = 0; i < items.size(); i++) {
            Node item = items.get(i);
            int size = item.isArray() ? item.raw().getAsJsonArray().size() : 0;
            float[] xy = size == 3 ? item.numbers(3, -4, 4) : item.numbers(2, -4, 4);
            if (xy == null) {
                if (size != 2 && size != 3) {
                    n.problems().error(item.path(), "must be [x, y] or [x, y, z] (in GUI widths) " + item.describe());
                }
                return new float[][]{{0, 0, 0}};
            }
            out[i] = new float[]{xy[0], xy[1], size == 3 ? xy[2] : 0};
        }
        return out;
    }

    private static FxDef.Target readTarget(Node n) {
        if (n.missing()) {
            return FxDef.Target.ALL;
        }
        if (n.isString()) {
            String group = n.asChoice("all", FxDef.Target.GROUPS);
            return new FxDef.Target(group, 0, 1, 0, false);
        }
        if (!n.expectObject()) {
            return FxDef.Target.ALL;
        }
        String group = n.choice("group", "all", FxDef.Target.GROUPS);
        float a = 0;
        float b = 1;
        switch (group) {
            case "wave" -> {
                a = n.num("from", 0, 0, 1);
                b = n.num("to", 1, 0, 1);
            }
            case "edge" -> a = n.num("width", 0.15f, 0, 0.5f);
            case "random" -> a = n.num("chance", 0.5f, 0, 1);
            default -> {
            }
        }
        int seed = n.integer("seed", 0, 0, 1_000_000);
        boolean invert = n.bool("invert", false);
        n.finish();
        return new FxDef.Target(group, a, b, seed, invert);
    }

    private static FxDef.Appearance readAppearance(Node n, Map<String, FxDef.TextureRole> textures) {
        Value defaultAlpha = new Value.Keys(new float[]{0, 0.75f, 1}, new float[]{1, 1, 0}, Easing.LINEAR, 0, 1, 0);
        if (n.missing() || !n.expectObject()) {
            return new FxDef.Appearance(Gradient.WHITE, false, defaultAlpha, Value.constant(0, 0, 1), Gradient.WHITE,
                    false, List.of(), null, List.of(), Value.constant(0, 0, 1));
        }
        Gradient color = Gradient.read(n.get("color"), Gradient.WHITE);
        boolean colorGlobal = "global".equals(n.choice("time", "local", List.of("local", "global")));
        Value alpha = n.has("alpha") ? Value.read(n.get("alpha"), 1, 0, 1) : defaultAlpha;
        Value emissive = Value.read(n.get("emissive"), 0, 0, 1);
        Gradient emissiveColor = Gradient.read(n.get("emissiveColor"), color);
        boolean fullbright = n.bool("fullbright", false);
        List<Integer> palette = new ArrayList<>();
        for (Node c : n.array("palette", FxLimits.MAX_GRADIENT_STOPS)) {
            Integer swatch = Gradient.parseColor(c.asStr(null));
            if (swatch == null) {
                n.problems().error(c.path(), "not a colour " + c.describe() + ", expected #RGB, #RRGGBB or #AARRGGBB");
            } else {
                palette.add(swatch);
            }
        }
        Value mosaic = Value.read(n.get("mosaic"), 0, 0, 1);
        List<FxDef.Overlay> overlays = new ArrayList<>();
        for (Node o : n.array("overlays", FxLimits.MAX_OVERLAYS)) {
            if (!o.expectObject()) {
                continue;
            }
            String texture = textureRef(o.get("texture"), FxDef.TextureRole.OVERLAY, textures, true);
            String blend = o.choice("blend", "normal", FxDef.Overlay.BLENDS);
            Value opacity = Value.read(o.get("opacity"), 1, 0, 1);
            Gradient tint = Gradient.read(o.get("color"), Gradient.WHITE);
            float[] scroll = o.get("scroll").numbers(2, -10, 10);
            float[] repeat = o.get("repeat").numbers(2, 0.05f, 32);
            o.finish();
            if (texture != null) {
                overlays.add(new FxDef.Overlay(texture, blend, opacity, tint, scroll == null ? 0 : scroll[0],
                        scroll == null ? 0 : scroll[1], repeat == null ? 1 : repeat[0], repeat == null ? 1 : repeat[1]));
            }
        }
        FxDef.Mask mask = null;
        Node m = n.get("mask");
        if (!m.missing() && m.expectObject()) {
            String texture = textureRef(m.get("texture"), FxDef.TextureRole.MASK, textures, true);
            Value threshold = Value.read(m.get("threshold"), 0, 0, 1);
            float softness = m.num("softness", 0.1f, 0.001f, 1);
            boolean invert = m.bool("invert", false);
            m.finish();
            if (texture != null) {
                mask = new FxDef.Mask(texture, threshold, softness, invert);
            }
        }
        n.finish();
        return new FxDef.Appearance(color, colorGlobal, alpha, emissive, emissiveColor, fullbright, List.copyOf(overlays), mask,
                List.copyOf(palette), mosaic);
    }

    private static FxDef.Emitter readEmitter(Node n, Map<String, FxDef.TextureRole> textures) {
        if (!n.expectObject()) {
            return null;
        }
        readLabel(n);
        boolean enabled = n.bool("enabled", true);
        String particle = null;
        String sprite = null;
        if (n.has("particle") == n.has("sprite")) {
            n.problems().error(n.path(), "needs exactly one of \"particle\" (a vanilla particle id) or \"sprite\" (a texture)");
            n.get("particle");
            n.get("sprite");
        } else if (n.has("particle")) {
            particle = resourceId(n.get("particle"));
        } else {
            sprite = textureRef(n.get("sprite"), FxDef.TextureRole.SPRITE, textures, true);
        }
        Value rate = Value.read(n.get("rate"), 1, 0, 50);
        int burst = n.integer("burst", 0, 0, 200);
        float from = n.num("from", 0, 0, 1);
        float to = n.num("to", 1, 0, 1);
        String zone = n.choice("zone", "surface", FxDef.Emitter.ZONES);
        FxDef.Point at = readPoint(n.get("at"), FxDef.Point.CENTER);
        FxDef.Point lineFrom = readPoint(n.get("lineFrom"), new FxDef.Point(FxDef.Point.Kind.FIXED, 0, 0));
        FxDef.Point lineTo = readPoint(n.get("lineTo"), new FxDef.Point(FxDef.Point.Kind.FIXED, 1, 0));
        float[] velocity = n.get("velocity").numbers(3, -1, 1);
        Value spread = Value.read(n.get("spread"), 0.01f, 0, 0.5f);
        Value life = Value.read(n.get("life"), 20, 1, FxLimits.MAX_PARTICLE_LIFE);
        float gravity = n.num("gravity", 0, -0.2f, 0.2f);
        float drag = n.num("drag", 0.02f, 0, 1);
        Gradient color = Gradient.read(n.get("color"), Gradient.WHITE);
        Value size = Value.read(n.get("size"), 0.06f, 0, 1);
        Value alpha = n.has("alpha") ? Value.read(n.get("alpha"), 1, 0, 1)
                : new Value.Keys(new float[]{0, 0.7f, 1}, new float[]{1, 1, 0}, Easing.LINEAR, 0, 1, 0);
        boolean emissive = n.bool("emissive", true);
        int max = n.integer("max", 200, 1, FxLimits.MAX_SPRITES_PER_WAVE);
        if (to < from) {
            n.problems().error(n.path(), "\"to\" is before \"from\"");
        }
        n.finish();
        if (particle == null && sprite == null || !enabled) {
            return null;
        }
        return new FxDef.Emitter(particle, sprite, rate, burst, from, to, zone, at, lineFrom, lineTo,
                velocity == null ? new float[]{0, 0.02f, 0} : velocity, spread, life, gravity, drag, color, size, alpha,
                emissive, max);
    }

    private static FxDef.Sound readSound(Node n) {
        if (!n.expectObject()) {
            return null;
        }
        readLabel(n);
        boolean enabled = n.bool("enabled", true);
        String sound = resourceId(n.get("sound"));
        if (!n.has("sound")) {
            n.problems().error(n.path() + ".sound", "is required (a vanilla sound id)");
        }
        float volume = n.num("volume", 1, 0, 1);
        float pitch = n.num("pitch", 1, 0.5f, 2);
        Node atNode = n.get("at");
        String at = "start";
        float time = 0;
        if (atNode.isNumber()) {
            at = "time";
            time = atNode.asNum(0, 0, 1);
        } else if (!atNode.missing()) {
            at = atNode.asChoice("start", List.of("start", "end"));
        }
        n.finish();
        return sound == null || !enabled ? null : new FxDef.Sound(sound, volume, pitch, at, time);
    }

    /** The editor's name for a layer, emitter or sound ("name", up to 32 characters; only shown in the editor). */
    private static void readLabel(Node n) {
        String label = n.str("name", "");
        if (label.length() > MAX_LABEL) {
            n.problems().warn(n.path() + ".name", "longer than " + MAX_LABEL + " characters");
        }
    }

    private static FxDef.Edge readEdge(Node n) {
        if (!n.expectObject()) {
            return null;
        }
        String direction = n.choice("direction", "random", FxDef.Edge.DIRECTIONS);
        boolean enter = n.bool("enter", false);
        boolean exit = n.bool("exit", true);
        float distance = n.num("distance", 1.3f, 0.1f, 4);
        float enterTime = n.num("enterTime", 0.25f, 0.01f, 1);
        float exitStart = n.num("exitStart", 0.4f, 0, 0.99f);
        Easing curve = easing(n.get("curve"), Easing.EASE_IN_CUBIC);
        n.finish();
        if (!enter && !exit) {
            n.problems().warn(n.path(), "neither \"enter\" nor \"exit\": the modifier does nothing");
        }
        return new FxDef.Edge(direction, enter, exit, distance, enterTime, exitStart, curve);
    }

    // --- small things ---

    static FxDef.Point readPoint(Node n, FxDef.Point def) {
        if (n.missing()) {
            return def;
        }
        if (n.isString()) {
            String name = n.asChoice("center", List.of("center", "source", "player"));
            return switch (name) {
                case "source" -> new FxDef.Point(FxDef.Point.Kind.SOURCE, 0.5f, 0.5f);
                case "player" -> new FxDef.Point(FxDef.Point.Kind.PLAYER, 0.5f, 0.5f);
                default -> FxDef.Point.CENTER;
            };
        }
        if (n.isArray()) {
            float[] uv = n.numbers(2, -2, 3);
            return uv == null ? def : new FxDef.Point(FxDef.Point.Kind.FIXED, uv[0], uv[1]);
        }
        n.problems().error(n.path(), "must be \"center\", \"source\", \"player\" or [u, v] " + n.describe());
        return def;
    }

    private static Easing easing(Node n, Easing def) {
        if (n.missing()) {
            return def;
        }
        String name = n.asStr(null);
        Easing easing = name == null ? null : Easing.byName(name);
        if (easing == null) {
            if (name != null) {
                n.problems().error(n.path(), "unknown easing \"" + name + "\", expected one of " + Easing.NAMES);
            }
            return def;
        }
        return easing;
    }

    private static String resourceId(Node n) {
        String id = n.asStr(null);
        if (id == null) {
            return null;
        }
        if (!RESOURCE_ID.matcher(id).matches()) {
            n.problems().error(n.path(), "not a valid id \"" + id + "\" (like minecraft:flame)");
            return null;
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static String textureRef(Node n, FxDef.TextureRole role, Map<String, FxDef.TextureRole> textures,
                                     boolean required) {
        if (n.missing()) {
            if (required) {
                n.problems().error(n.path(), "is required (a texture name)");
            }
            return null;
        }
        String name = n.asStr(null);
        if (name == null) {
            return null;
        }
        if (name.endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        if (!TEXTURE_NAME.matcher(name).matches() || name.contains("..")) {
            n.problems().error(n.path(), "not a valid texture name \"" + name + "\" (a-z, 0-9, _ . - /, optional namespace:)");
            return null;
        }
        // The strictest size limit wins when a texture has several roles.
        FxDef.TextureRole old = textures.get(name);
        if (old == null || role.ordinal() < old.ordinal()) {
            textures.put(name, role);
        }
        return name;
    }

    private static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\\p{Cntrl}", "").trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
