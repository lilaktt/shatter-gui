package net.shattergui.params;

import java.util.Locale;

/**
 * Effect quality ({@code "effectQuality"}): how many pieces and particles every effect makes, and on {@link #LOW} also
 * simpler effects — no block collisions, no glow layer, no texture overlays. "medium" changes nothing. The factors of
 * {@link #LOW} and {@link #HIGH} are the ones the "subtle" and "cinematic" presets had before quality existed, so an
 * older config (quality taken from its preset) looks the same.
 */
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

    /** False on {@link #LOW}: no block collisions, no glow layer and no texture overlays. */
    public boolean full() {
        return full;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** By config name; unknown or null → null. */
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
