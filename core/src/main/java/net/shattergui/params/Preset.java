package net.shattergui.params;

import java.util.Locale;

/**
 * Overall style on top of the parameters: the length of every effect, and the {@link Quality} and number of effects at
 * once it comes with (choosing a preset in the editor sets both; they can be changed on their own afterwards). "normal"
 * changes nothing.
 */
public enum Preset {
    MINIMAL(0.7f, Quality.LOW, 1),
    NORMAL(1f, Quality.MEDIUM, 2),
    CINEMATIC(1.3f, Quality.HIGH, 3);

    private final float duration;
    private final Quality quality;
    private final int maxConcurrent;

    Preset(float duration, Quality quality, int maxConcurrent) {
        this.duration = duration;
        this.quality = quality;
        this.maxConcurrent = maxConcurrent;
    }

    public float duration() {
        return duration;
    }

    /** The quality that goes with this preset. */
    public Quality quality() {
        return quality;
    }

    /** The number of effects at once that goes with this preset. */
    public int maxConcurrent() {
        return maxConcurrent;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** By config name; unknown or null → normal. */
    public static Preset byId(String id) {
        if (id != null) {
            for (Preset preset : values()) {
                if (preset.id().equalsIgnoreCase(id.trim())) {
                    return preset;
                }
            }
        }
        return NORMAL;
    }
}
