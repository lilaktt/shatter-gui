package net.shattergui.params;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BundledParams {
    public static final String LANG_GROUP = "bundled";

    static final Map<String, Integer> DURATIONS = new LinkedHashMap<>();
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
        for (Object[] quick : new Object[][]{{"pop", 9}, {"slide", 10}, {"flip", 10}, {"recede", 12}, {"door", 14},
                {"ripple", 16}, {"twirl", 11}, {"drop", 18}, {"accordion", 12}, {"peel", 13}, {"spring", 12},
                {"tear", 13}, {"iris", 14}}) {
            DURATIONS.put((String) quick[0], (Integer) quick[1]);
            QUICK.add((String) quick[0]);
        }
    }

    public static boolean quick(String id) {
        return QUICK.contains(id);
    }

    public record Set(Param.Int duration, Param.Float intensity, Param.Float particles, Param.Float soundVolume) {
    }

    private BundledParams() {
    }

    public static List<String> ids() {
        return List.copyOf(DURATIONS.keySet());
    }

    public static int fileDuration(String id) {
        return DURATIONS.getOrDefault(id, 40);
    }

    public static Set of(String id) {
        return Params.BUNDLED.get(id);
    }
}