package net.shattergui.ui;

public final class UiClock {
    private static final float MAX_DT = 0.1f;

    private static long last = System.nanoTime();
    private static float dt;
    private static double time;
    private static long frame;

    private UiClock() {
    }

    public static void frame() {
        long now = System.nanoTime();
        dt = Math.clamp((now - last) / 1e9f, 0f, MAX_DT);
        last = now;
        time += dt;
        frame++;
    }

    public static long frameNumber() {
        return frame;
    }

    public static float dt() {
        return dt;
    }

    public static double time() {
        return time;
    }
}
