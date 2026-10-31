package net.shattergui.fx;

import java.util.List;

/**
 * A colour over time: stops at times 0..1, linearly blended (alpha included). Written as a list of colours (spread
 * evenly) or of stops: {@code ["#ffffff", "#aa50ff"]} or {@code [{"t": 0, "color": "#fff"}, {"t": 0.7, "color": "#a5f"}]}.
 * Colours: {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}.
 */
public final class Gradient {
    public static final Gradient WHITE = new Gradient(new float[]{0}, new int[]{0xFFFFFFFF});
    final float[] times;
    final int[] colors;

    Gradient(float[] times, int[] colors) {
        this.times = times;
        this.colors = colors;
    }

    public static Gradient solid(int argb) {
        return new Gradient(new float[]{0}, new int[]{argb});
    }

    public int at(float t) {
        t = Math.clamp(t, 0f, 1f);
        if (t <= times[0]) {
            return colors[0];
        }
        int last = times.length - 1;
        if (t >= times[last]) {
            return colors[last];
        }
        for (int i = 0; i < last; i++) {
            if (t <= times[i + 1]) {
                float span = times[i + 1] - times[i];
                return lerp(colors[i], colors[i + 1], span <= 0 ? 1 : (t - times[i]) / span);
            }
        }
        return colors[last];
    }

    public static int lerp(int a, int b, float t) {
        int aa = a >>> 24;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** #RGB, #RRGGBB, #AARRGGBB → ARGB, or null. */
    public static Integer parseColor(String text) {
        if (text == null || !text.startsWith("#")) {
            return null;
        }
        String hex = text.substring(1);
        try {
            return switch (hex.length()) {
                case 3 -> {
                    int r = Integer.parseInt(hex.substring(0, 1), 16);
                    int g = Integer.parseInt(hex.substring(1, 2), 16);
                    int b = Integer.parseInt(hex.substring(2, 3), 16);
                    yield 0xFF000000 | (r * 17 << 16) | (g * 17 << 8) | b * 17;
                }
                case 6 -> 0xFF000000 | Integer.parseInt(hex, 16);
                case 8 -> (int) Long.parseLong(hex, 16);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reads a gradient from JSON for showing it: anything unreadable gives the default. */
    public static Gradient fromJson(com.google.gson.JsonElement json, Gradient def) {
        try {
            return read(new Node(json, "", new FxProblems()), def);
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** #AARRGGBB (or #RRGGBB when opaque) for an ARGB colour. */
    public static String format(int argb) {
        return (argb >>> 24) == 0xFF ? String.format(java.util.Locale.ROOT, "#%06x", argb & 0xFFFFFF) : String.format(java.util.Locale.ROOT, "#%08x", argb);
    }

    static Gradient read(Node node, Gradient def) {
        if (node.missing()) {
            return def;
        }
        if (node.isString()) {
            Integer c = parseColor(node.asStr(null));
            if (c == null) {
                node.problems().error(node.path(), "not a colour " + node.describe() + ", expected #RGB, #RRGGBB or #AARRGGBB");
                return def;
            }
            return solid(c);
        }
        List<Node> items = node.asArray(FxLimits.MAX_GRADIENT_STOPS);
        if (!node.isArray()) {
            return def;
        }
        if (items.isEmpty()) {
            node.problems().error(node.path(), "needs at least one colour");
            return def;
        }
        float[] times = new float[items.size()];
        int[] colors = new int[items.size()];
        float last = -1;
        for (int i = 0; i < items.size(); i++) {
            Node item = items.get(i);
            float time;
            String colorText;
            if (item.isString()) {
                time = items.size() == 1 ? 0 : (float) i / (items.size() - 1);
                colorText = item.asStr(null);
            } else if (item.isObject()) {
                time = item.num("t", items.size() == 1 ? 0 : (float) i / (items.size() - 1), 0, 1);
                colorText = item.str("color", null);
                item.finish();
            } else {
                node.problems().error(item.path(), "must be a colour or {\"t\", \"color\"} " + item.describe());
                return def;
            }
            Integer color = parseColor(colorText);
            if (color == null) {
                node.problems().error(item.path(), "not a colour \"" + colorText + "\", expected #RGB, #RRGGBB or #AARRGGBB");
                return def;
            }
            if (time < last) {
                node.problems().error(item.path(), "stop times must go up");
                return def;
            }
            last = time;
            times[i] = time;
            colors[i] = color;
        }
        return new Gradient(times, colors);
    }
}
