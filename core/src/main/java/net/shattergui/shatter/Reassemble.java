package net.shattergui.shatter;

public final class Reassemble {
    public static final float MIN_ALIVE = 0.5f;
    public static final float MIN_OPACITY = 0.35f;
    public static final int BASE_TICKS = 18;
    public static final int MIN_TICKS = 4;
    public static final int FADE_TICKS = 4;
    public static final float APPEAR_FROM = 2f / 3f;

    private Reassemble() {
    }

    public static boolean eligible(int alive, int total, double meanOpacity) {
        return total > 0 && alive >= MIN_ALIVE * total && meanOpacity >= MIN_OPACITY;
    }

    public static int duration(float openSpeed, float durationScale) {
        return Math.max(MIN_TICKS, Math.round(BASE_TICKS * durationScale / Math.max(0.1f, openSpeed)));
    }

    public static float ease(float t) {
        float x = Math.clamp(t, 0f, 1f);
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    public static float alpha(float start, float eased) {
        float s = Math.clamp(start, 0f, 1f);
        return s + (1f - s) * Math.clamp(eased, 0f, 1f);
    }

    public static float appear(float eased) {
        float x = Math.clamp((eased - APPEAR_FROM) / (1f - APPEAR_FROM), 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    public static float fade(double ticksSinceLanded) {
        return (float) Math.clamp(1.0 - ticksSinceLanded / FADE_TICKS, 0.0, 1.0);
    }

    public static int light(int start, float eased) {
        int block = (start >> 4) & 0xF;
        int sky = (start >> 20) & 0xF;
        float e = Math.clamp(eased, 0f, 1f);
        int b = Math.round(block + (15 - block) * e);
        int s = Math.round(sky + (15 - sky) * e);
        return (s << 20) | (b << 4);
    }
}
