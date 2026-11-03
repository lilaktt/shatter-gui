package net.shattergui.effect;

/**
 * Output of {@link ShatterEffect#tint}: how a shard looks this frame. Reused per shard; the renderer resets it to the
 * defaults below before each call.
 */
public final class ShardTint {
    /** ARGB at the shard's centroid. */
    public int center;
    /** ARGB at the shard's corners. */
    public int edge;
    /** Size around the centroid, 1 = as cut. */
    public float scale;
    /** Skip the facing-based shading (for shards that stay flat on the plane, like the GUI itself). */
    public boolean unlit;
    /** Strength 0..1 of a fullbright glow layer drawn over the shard, in {@link #glowColor}. */
    public float glow;
    /** RGB of the glow layer. */
    public int glowColor;
    /**
     * Ticks the face shading has had to ease in (the GUI is unlit, so shading starts at none), or NaN for the shard's
     * age. Played backwards, an effect ends where the shard was cut: its shading must ease out, not in.
     */
    public float lightAge;

    public void set(int color) {
        center = color;
        edge = color;
    }

    /** Back to the defaults; called by the renderer before {@link ShatterEffect#tint}. */
    public void reset() {
        center = 0xFFFFFFFF;
        edge = 0xFFFFFFFF;
        scale = 1f;
        unlit = false;
        glow = 0f;
        glowColor = 0;
        lightAge = Float.NaN;
    }
}
