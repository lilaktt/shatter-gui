package net.shattergui.debug;

import net.shattergui.ShatterMod;

import java.util.Locale;

public final class CloseWatchdog {
    public static final long LIMIT_MS = 50;
    private static final long LIMIT_NANOS = LIMIT_MS * 1_000_000L;
    private static final int WATCHED_FRAMES = 10;

    private static String screen = "";
    private static boolean warned;
    private static int framesLeft;
    private static String slowest;
    private static long slowestNanos;
    private static int warnings;

    private CloseWatchdog() {
    }

    public static void begin(String screenName) {
        screen = screenName;
        warned = false;
        framesLeft = WATCHED_FRAMES;
        slowest = "none";
        slowestNanos = 0;
    }

    public static void frameEnd() {
        if (framesLeft > 0) {
            framesLeft--;
        }
    }

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

    public static int warnings() {
        return warnings;
    }

    private static void warn(String step, long nanos) {
        if (warned) {
            return;
        }
        warned = true;
        warnings++;
        ShatterMod.LOGGER.warn("Closing {} took {} ms in step \"{}\" (over {} ms)", screen,
                String.format(Locale.ROOT, "%.1f", nanos / 1e6), step, LIMIT_MS);
    }
}
