package net.shattergui.debug;

import net.shattergui.ShatterGuiClient;

import java.util.Locale;

/**
 * Always on (unlike {@link ShatterProfiler}): closing a screen must never hold up the game. From the call that closes
 * the screen until a few frames later (capture, Spatial GUI copy, spawn), every step is timed; one over
 * {@link #LIMIT_MS} ms logs a single warning line with the step's name (at most one per close).
 */
public final class CloseWatchdog {
    public static final long LIMIT_MS = 50;
    private static final long LIMIT_NANOS = LIMIT_MS * 1_000_000L;
    /** Frames after the close in which deferred steps are still timed. */
    private static final int WATCHED_FRAMES = 10;

    private static String screen = "";
    private static boolean warned;
    private static int framesLeft;
    private static String slowest;
    private static long slowestNanos;
    private static int warnings;

    private CloseWatchdog() {
    }

    /** HEAD of {@code Minecraft#setScreen(null)}: a screen starts closing. */
    public static void begin(String screenName) {
        screen = screenName;
        warned = false;
        framesLeft = WATCHED_FRAMES;
        slowest = "none";
        slowestNanos = 0;
    }

    /** One frame of the watched close is over. */
    public static void frameEnd() {
        if (framesLeft > 0) {
            framesLeft--;
        }
    }

    /** A step of the close ran from {@code startNanos} until now. Returns now, for the next step. */
    public static long step(String name, long startNanos) {
        long now = System.nanoTime();
        if (framesLeft <= 0) {
            return now;
        }
        long took = now - startNanos;
        if (took > slowestNanos) {
            slowest = name;
            slowestNanos = took;
        }
        if (took > LIMIT_NANOS) {
            warn(name, took);
        }
        return now;
    }

    /**
     * RETURN of {@code setScreen(null)}: the whole call, vanilla included. If it was slow but none of Shatter GUI's
     * steps was, the time went to vanilla or other mods ({@code Screen#removed}, closing the container).
     */
    public static void endCall(long startNanos) {
        if (framesLeft <= 0) {
            return;
        }
        long took = System.nanoTime() - startNanos;
        if (took > LIMIT_NANOS && !warned) {
            warn(String.format(Locale.ROOT, "setScreen(null) outside Shatter GUI (vanilla Screen#removed, closing the "
                    + "container, other mods); slowest own step: %s %.1f ms", slowest, slowestNanos / 1e6), took);
        }
    }

    /** Warnings logged in this session (tests). */
    public static int warnings() {
        return warnings;
    }

    private static void warn(String step, long nanos) {
        if (warned) {
            return;
        }
        warned = true;
        warnings++;
        ShatterGuiClient.LOGGER.warn("Closing {} took {} ms in step \"{}\" (over {} ms)", screen,
                String.format(Locale.ROOT, "%.1f", nanos / 1e6), step, LIMIT_MS);
    }
}
