package net.shattergui.params;

import java.util.Locale;

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

    public Quality quality() {
        return quality;
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

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
