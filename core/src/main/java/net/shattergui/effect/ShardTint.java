package net.shattergui.effect;

public final class ShardTint {
    public int center;
    public int edge;
    public float scale;
    public boolean unlit;
    public float glow;
    public int glowColor;
    public float lightAge;

    public void set(int color) {
        center = color;
        edge = color;
    }

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
