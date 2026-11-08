package net.shattergui.params;

import java.util.Locale;

public enum Quality {
    LOW(0.35f, 0.5f, false),
    MEDIUM(1f, 1f, true),
    HIGH(1.5f, 1.5f, true);

    private final float particles;
    private final float shards;
    private final boolean full;

    Quality(float particles, float shards, boolean full) {
        this.particles = particles;
        this.shards = shards;
        this.full = full;
    }

    public float particles() {
        return particles;
    }

    public float shards() {
        return shards;
    }

    public boolean full() {
        return full;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Quality byId(String id) {
        if (id != null) {
            for (Quality quality : values()) {
                if (quality.id().equalsIgnoreCase(id.trim())) {
                    return quality;
                }
            }
        }
        return null;
    }
}
