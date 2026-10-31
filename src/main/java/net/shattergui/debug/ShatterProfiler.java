package net.shattergui.debug;

import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;

import java.util.Locale;

/**
 * Timing log for the close → capture → spawn → first render pipeline. Enabled by {@code "profiling": true} in the
 * config. Every line starts with {@code [ShatterProfile]} and the number of the close in this session.
 */
public final class ShatterProfiler {
    /** How many frames after a close get their total render time and frame interval logged. */
    private static final int FRAMES_TO_LOG = 8;

    private static int closeNumber;
    private static int openNumber;
    private static int framesLeft;
    private static int frameIndex;
    private static long frameStart;
    private static long lastFrameStart;
    /** When the screen of the current close was asked to close (HEAD of {@code setScreen(null)}), 0 once logged. */
    private static long closeRequested;
    /** Milliseconds from the last close request to the first frame of its effect, or -1. */
    private static double lastCloseLatencyMs = -1;

    private ShatterProfiler() {
    }

    public static boolean enabled() {
        return ShatterConfig.get().profiling;
    }

    public static long now() {
        return System.nanoTime();
    }

    /** A screen is closing and will shatter. Starts a new numbered close. */
    public static void beginClose(String screenKey, String effect, long requestedNanos) {
        closeNumber++;
        framesLeft = FRAMES_TO_LOG;
        frameIndex = 0;
        closeRequested = requestedNanos;
        lastCloseLatencyMs = -1;
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] close #{} of this session: screen={} effect={}",
                    closeNumber, screenKey, effect);
        }
    }

    /** A screen is opening with an open effect. Starts a new numbered open (its lines read "open #n"). */
    public static void beginOpen(String screenKey, String effect, String source) {
        openNumber++;
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] open #{} of this session: screen={} effect={} source={}",
                    openNumber, screenKey, effect, source);
        }
    }

    /** How many screens have started an open effect (fly-in) in this session. */
    public static int openCount() {
        return openNumber;
    }

    public static void openStep(String name, long startNanos) {
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] open #{} {}: {} ms", openNumber, name, ms(now() - startNanos));
        }
    }

    public static void openValue(String name, Object value) {
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] open #{} {}: {}", openNumber, name, value);
        }
    }

    /** How many screens have shattered in this session. */
    public static int closeCount() {
        return closeNumber;
    }

    /** Logs the time since {@code startNanos} for one step of the current close. */
    public static void step(String name, long startNanos) {
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] close #{} {}: {} ms", closeNumber, name, ms(now() - startNanos));
        }
    }

    public static void value(String name, Object value) {
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] close #{} {}: {}", closeNumber, name, value);
        }
    }

    /**
     * The first frame of a close effect was drawn: logs the time since the screen was asked to close (for Esc, the key
     * press, which closes the screen in the same call).
     */
    public static void closeEffectDrawn() {
        if (closeRequested == 0) {
            return;
        }
        lastCloseLatencyMs = (now() - closeRequested) / 1e6;
        closeRequested = 0;
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] close #{} close.latency (setScreen(null) -> first frame of the effect, frame +{}): {} ms",
                    closeNumber, frameIndex, String.format(Locale.ROOT, "%.3f", lastCloseLatencyMs));
        }
    }

    /** Milliseconds from the last close request to the first frame of its effect, or -1 if not drawn yet. */
    public static double lastCloseLatencyMs() {
        return lastCloseLatencyMs;
    }

    /** HEAD of {@code GameRenderer#render}. */
    public static void frameStart() {
        long t = now();
        if (framesLeft > 0 && enabled() && lastFrameStart != 0) {
            // Interval = everything between two frames: ticks, input, buffer swap, driver waits.
            ShatterGuiClient.LOGGER.info("[ShatterProfile] close #{} frame +{} interval: {} ms",
                    closeNumber, frameIndex, ms(t - lastFrameStart));
        }
        frameStart = t;
        lastFrameStart = t;
    }

    /** RETURN of {@code GameRenderer#render}. */
    public static void frameEnd() {
        if (framesLeft <= 0) {
            return;
        }
        if (enabled()) {
            ShatterGuiClient.LOGGER.info("[ShatterProfile] close #{} frame +{} GameRenderer.render: {} ms",
                    closeNumber, frameIndex, ms(now() - frameStart));
        }
        frameIndex++;
        framesLeft--;
    }

    public static int frameIndex() {
        return frameIndex;
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
