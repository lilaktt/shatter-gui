package net.shattergui.fx;

import java.util.List;

/**
 * A number that may change over time and differ per shard: a constant, a random range, an easing curve, a cubic Bézier
 * or keyframes. {@code t} is the time base (0..1, the shard's own progress or the whole effect's) and {@code rnd} a
 * random number fixed per shard (0..1). Every result is clamped to the range given when it was read.
 *
 * <pre>
 *   0.5
 *   {"random": [0.2, 0.6]}
 *   {"curve": "easeOutCubic", "from": 0, "to": 1}
 *   {"bezier": [0.25, 0.1, 0.25, 1.0], "from": 1, "to": 0}
 *   {"keys": [[0, 0], [0.3, 1], [1, 0]], "ease": "smooth"}
 *   any object form + {"jitter": 0.2}: per-shard ×(1 ± 0.2)
 * </pre>
 */
public abstract class Value {
    final float min;
    final float max;
    /** Per-shard multiplier spread, 0..1. */
    final float jitter;

    Value(float min, float max, float jitter) {
        this.min = min;
        this.max = max;
        this.jitter = jitter;
    }

    /** The value at time {@code t} for a shard with random {@code rnd}. */
    public final float at(float t, float rnd) {
        float v = raw(Math.clamp(t, 0f, 1f), rnd);
        if (jitter > 0) {
            // A second, independent random from the same seed.
            float r2 = fract(rnd * 7.31f + 0.37f);
            v *= 1f + (r2 * 2f - 1f) * jitter;
        }
        return Math.clamp(v, min, max);
    }

    abstract float raw(float t, float rnd);

    /** True if the value never changes (constants), so callers can skip per-tick work. */
    public boolean isConstant() {
        return false;
    }

    public static Value constant(float value, float min, float max) {
        return new Const(Math.clamp(value, min, max), min, max);
    }

    static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    // --- forms ---

    static final class Const extends Value {
        final float value;

        Const(float value, float min, float max) {
            super(min, max, 0);
            this.value = value;
        }

        @Override
        float raw(float t, float rnd) {
            return value;
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    static final class Range extends Value {
        final float a;
        final float b;

        Range(float a, float b, float min, float max, float jitter) {
            super(min, max, jitter);
            this.a = a;
            this.b = b;
        }

        @Override
        float raw(float t, float rnd) {
            return a + (b - a) * rnd;
        }
    }

    static final class Curve extends Value {
        final Easing easing;
        final float from;
        final float to;

        Curve(Easing easing, float from, float to, float min, float max, float jitter) {
            super(min, max, jitter);
            this.easing = easing;
            this.from = from;
            this.to = to;
        }

        @Override
        float raw(float t, float rnd) {
            return from + (to - from) * easing.apply(t);
        }
    }

    /** CSS-style cubic-bezier(x1, y1, x2, y2) from (0,0) to (1,1); x is time, y the progress. */
    static final class Bezier extends Value {
        final float x1;
        final float y1;
        final float x2;
        final float y2;
        final float from;
        final float to;

        Bezier(float[] p, float from, float to, float min, float max, float jitter) {
            super(min, max, jitter);
            this.x1 = p[0];
            this.y1 = p[1];
            this.x2 = p[2];
            this.y2 = p[3];
            this.from = from;
            this.to = to;
        }

        private static float cubic(float a, float b, float s) {
            float u = 1 - s;
            return 3 * u * u * s * a + 3 * u * s * s * b + s * s * s;
        }

        /** y for x = t (bisection on the monotonic x(s), x1 and x2 being in 0..1). */
        static float solve(float x1, float y1, float x2, float y2, float t) {
            float lo = 0;
            float hi = 1;
            float s = t;
            for (int i = 0; i < 24; i++) {
                s = (lo + hi) / 2;
                float x = cubic(x1, x2, s);
                if (x < t) {
                    lo = s;
                } else {
                    hi = s;
                }
            }
            return cubic(y1, y2, s);
        }

        @Override
        float raw(float t, float rnd) {
            return from + (to - from) * solve(x1, y1, x2, y2, t);
        }
    }

    /** Keyframes (time ascending), eased between neighbours. */
    static final class Keys extends Value {
        final float[] times;
        final float[] values;
        final Easing ease;

        Keys(float[] times, float[] values, Easing ease, float min, float max, float jitter) {
            super(min, max, jitter);
            this.times = times;
            this.values = values;
            this.ease = ease;
        }

        @Override
        float raw(float t, float rnd) {
            if (t <= times[0]) {
                return values[0];
            }
            int last = times.length - 1;
            if (t >= times[last]) {
                return values[last];
            }
            for (int i = 0; i < last; i++) {
                if (t <= times[i + 1]) {
                    float span = times[i + 1] - times[i];
                    float f = span <= 0 ? 1 : (t - times[i]) / span;
                    return values[i] + (values[i + 1] - values[i]) * ease.apply(f);
                }
            }
            return values[last];
        }
    }

    /** Reads a value from JSON for showing it (curve drawing): anything unreadable gives the default. */
    public static Value fromJson(com.google.gson.JsonElement json, float def, float min, float max) {
        try {
            return read(new Node(json, "", new FxProblems()), def, min, max);
        } catch (RuntimeException e) {
            return constant(def, min, max);
        }
    }

    // --- reading ---

    /**
     * Reads a value at {@code node}: missing → {@code def}; a number; or one of the object forms. {@code min}/{@code max}
     * bound every result (numbers outside are clamped with a warning).
     */
    static Value read(Node node, float def, float min, float max) {
        if (node.missing()) {
            return constant(def, min, max);
        }
        if (node.isNumber()) {
            return constant(node.asNum(def, min, max), min, max);
        }
        if (!node.isObject()) {
            node.problems().error(node.path(), "must be a number or a curve object " + node.describe());
            return constant(def, min, max);
        }
        float jitter = node.num("jitter", 0, 0, 1);
        Value value;
        if (node.has("value")) {
            value = new Range(node.num("value", def, min, max), node.num("value", def, min, max), min, max, jitter);
        } else if (node.has("random")) {
            float[] range = node.get("random").numbers(2, min, max);
            value = range == null ? constant(def, min, max) : new Range(range[0], range[1], min, max, jitter);
        } else if (node.has("curve")) {
            String name = node.str("curve", "linear");
            Easing easing = Easing.byName(name);
            if (easing == null) {
                node.problems().error(node.path() + ".curve", "unknown easing \"" + name + "\", expected one of " + Easing.NAMES);
                easing = Easing.LINEAR;
            }
            float[] ft = fromTo(node, def, min, max);
            value = new Curve(easing, ft[0], ft[1], min, max, jitter);
        } else if (node.has("bezier")) {
            float[] p = node.get("bezier").numbers(4, -2, 3);
            if (p != null && (p[0] < 0 || p[0] > 1 || p[2] < 0 || p[2] > 1)) {
                node.problems().error(node.path() + ".bezier", "x1 and x2 must be between 0 and 1 (like CSS cubic-bezier)");
                p = null;
            }
            float[] ft = fromTo(node, def, min, max);
            value = p == null ? constant(def, min, max) : new Bezier(p, ft[0], ft[1], min, max, jitter);
        } else if (node.has("keys")) {
            value = readKeys(node, def, min, max, jitter);
        } else {
            node.problems().error(node.path(), "needs one of: value, random, curve, bezier, keys");
            value = constant(def, min, max);
        }
        node.finish();
        return value;
    }

    private static float[] fromTo(Node node, float def, float min, float max) {
        if (!node.has("from") || !node.has("to")) {
            node.problems().error(node.path(), "a curve needs \"from\" and \"to\"");
        }
        return new float[]{node.num("from", def, min, max), node.num("to", def, min, max)};
    }

    private static Value readKeys(Node node, float def, float min, float max, float jitter) {
        List<Node> keys = node.array("keys", FxLimits.MAX_KEYS);
        String easeName = node.str("ease", "linear");
        Easing ease = Easing.byName(easeName);
        if (ease == null) {
            node.problems().error(node.path() + ".ease", "unknown easing \"" + easeName + "\", expected one of " + Easing.NAMES);
            ease = Easing.LINEAR;
        }
        if (keys.isEmpty()) {
            node.problems().error(node.path() + ".keys", "needs at least one [time, value] pair");
            return constant(def, min, max);
        }
        float[] times = new float[keys.size()];
        float[] values = new float[keys.size()];
        float last = -1;
        for (int i = 0; i < keys.size(); i++) {
            // Read unclamped: time and value are checked below with clear messages.
            float[] pair = keys.get(i).numbers(2, -Float.MAX_VALUE, Float.MAX_VALUE);
            if (pair == null) {
                if (!keys.get(i).isArray()) {
                    node.problems().error(keys.get(i).path(), "must be a [time, value] pair " + keys.get(i).describe());
                }
                return constant(def, min, max);
            }
            float time = pair[0];
            if (time < 0 || time > 1) {
                node.problems().error(keys.get(i).path(), "time must be between 0 and 1");
                return constant(def, min, max);
            }
            if (time < last) {
                node.problems().error(keys.get(i).path(), "times must go up (" + Node.fmt(time) + " after " + Node.fmt(last) + ")");
                return constant(def, min, max);
            }
            last = time;
            times[i] = time;
            float v = pair[1];
            if (v < min || v > max) {
                node.problems().warn(keys.get(i).path(), "value out of range " + Node.fmt(min) + ".." + Node.fmt(max) + ", clamped");
                v = Math.clamp(v, min, max);
            }
            values[i] = v;
        }
        return new Keys(times, values, ease, min, max, jitter);
    }
}
