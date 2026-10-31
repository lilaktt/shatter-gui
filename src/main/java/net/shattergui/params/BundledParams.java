package net.shattergui.params;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effects shipped with the mod as data files ({@code assets/shatter_gui/effects/<id>.json}, schema 2) and their
 * settings: every one has the same four (length, strength of the motion, particles, volume), registered in
 * {@link Params} under the effect's id and translated once ({@code param.shatter_gui.bundled.*}).
 */
public final class BundledParams {
    /** Translation group of the shared settings. */
    public static final String LANG_GROUP = "bundled";

    /** Ids in the order they are listed, with the length (ticks) their file has. */
    static final Map<String, Integer> DURATIONS = new LinkedHashMap<>();
    /** The quick everyday effects (their duration setting goes up to 2 s instead of 20 s). */
    static final java.util.Set<String> QUICK = new java.util.LinkedHashSet<>();

    static {
        DURATIONS.put("frost", 46);
        DURATIONS.put("sand", 44);
        DURATIONS.put("portal", 40);
        DURATIONS.put("runes", 46);
        DURATIONS.put("lightning", 34);
        DURATIONS.put("glitch", 26);
        DURATIONS.put("paper", 36);
        DURATIONS.put("tiles", 40);
        DURATIONS.put("explosion", 30);
        DURATIONS.put("confetti", 44);
        DURATIONS.put("bubbles", 46);
        DURATIONS.put("soul_fire", 46);
        DURATIONS.put("pixelate", 40);
        DURATIONS.put("blinds", 28);
        // Quick everyday effects: 0.3–0.9 s, one to a few dozen pieces, few particles.
        for (Object[] quick : new Object[][]{{"pop", 9}, {"slide", 10}, {"flip", 10}, {"recede", 12}, {"door", 14},
                {"ripple", 16}, {"twirl", 11}, {"drop", 18}, {"accordion", 12}, {"peel", 13}, {"spring", 12},
                {"tear", 13}, {"iris", 14}}) {
            DURATIONS.put((String) quick[0], (Integer) quick[1]);
            QUICK.add((String) quick[0]);
        }
    }

    /** A quick everyday effect (short, light). */
    public static boolean quick(String id) {
        return QUICK.contains(id);
    }

    /** The settings of one effect. */
    public record Set(Param.Int duration, Param.Float intensity, Param.Float particles, Param.Float soundVolume) {
    }

    private BundledParams() {
    }

    /** The shipped effects' ids, in list order. */
    public static List<String> ids() {
        return List.copyOf(DURATIONS.keySet());
    }

    /** Length (ticks) its file has: the default of its "duration" setting. */
    public static int fileDuration(String id) {
        return DURATIONS.getOrDefault(id, 40);
    }

    /** The settings of a shipped effect, or null for any other id. */
    public static Set of(String id) {
        return Params.BUNDLED.get(id);
    }
}