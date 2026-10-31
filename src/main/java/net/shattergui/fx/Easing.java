package net.shattergui.fx;

import java.util.List;
import java.util.Locale;

/** Easing presets: 0..1 → 0..1 (some overshoot a little: back, elastic). */
public enum Easing {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC, EASE_IN_QUART,
    EASE_OUT_QUART, EASE_IN_BACK, EASE_OUT_BACK, EASE_OUT_BOUNCE, EASE_OUT_ELASTIC, SMOOTH, STEP, PULSE;

    /** Config names: "linear", "easeIn", "easeOutCubic", … */
    public static final List<String> NAMES = java.util.Arrays.stream(values()).map(Easing::configName).toList();

    public String configName() {
        String[] parts = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    public static Easing byName(String name) {
        for (Easing easing : values()) {
            if (easing.configName().equalsIgnoreCase(name)) {
                return easing;
            }
        }
        return null;
    }

    public float apply(float t) {
        t = Math.clamp(t, 0f, 1f);
        return switch (this) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1 - (float) Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            case EASE_IN_QUART -> t * t * t * t;
            case EASE_OUT_QUART -> 1 - (float) Math.pow(1 - t, 4);
            case EASE_IN_BACK -> {
                float c1 = 1.70158f;
                yield (c1 + 1) * t * t * t - c1 * t * t;
            }
            case EASE_OUT_BACK -> {
                float c1 = 1.70158f;
                float u = t - 1;
                yield 1 + (c1 + 1) * u * u * u + c1 * u * u;
            }
            case EASE_OUT_BOUNCE -> bounce(t);
            case EASE_OUT_ELASTIC -> t == 0 || t == 1 ? t
                    : (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
            case SMOOTH -> t * t * (3 - 2 * t);
            case STEP -> t < 1f ? 0f : 1f;
            case PULSE -> (float) Math.sin(Math.PI * t);
        };
    }

    private static float bounce(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5 / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        }
        t -= 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }
}
