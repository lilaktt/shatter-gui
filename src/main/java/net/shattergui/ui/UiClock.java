package net.shattergui.ui;

/**
 * Frame clock of the editor: {@link #frame()} is called once per rendered frame and gives every animation the real
 * time since the previous frame (clamped, so a hitch or a paused window does not jump animations).
 */
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

    /** Number of the current frame (to do something once per frame). */
    public static long frameNumber() {
        return frame;
    }

    /** Seconds since the previous frame. */
    public static float dt() {
        return dt;
    }

    /** Seconds of editor time (only advances while frames are drawn). */
    public static double time() {
        return time;
    }
}
