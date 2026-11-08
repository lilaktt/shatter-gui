package net.shattergui.debug;

import net.shattergui.ShatterMod;
import net.shattergui.config.ShatterConfig;

import java.util.Locale;

public final class ShatterProfiler {
    private static final int FRAMES_TO_LOG = 8;

    private static int closeNumber;
    private static int openNumber;
    private static int framesLeft;
    private static int frameIndex;
    private static long frameStart;
    private static long lastFrameStart;
    private static long closeRequested;
    private static double lastCloseLatencyMs = -1;

    private ShatterProfiler() {
    }

    public static boolean enabled() {
        return ShatterConfig.get().profiling;
    }

    public static long now() {
        return System.nanoTime();
    }

    public static void beginClose(String screenKey, String effect, long requestedNanos) {
        closeNumber++;
        framesLeft = FRAMES_TO_LOG;
        frameIndex = 0;
        closeRequested = requestedNanos;
        lastCloseLatencyMs = -1;
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] close #{} of this session: screen={} effect={}",
                    closeNumber, screenKey, effect);
        }
    }

    public static void beginOpen(String screenKey, String effect, String source) {
        openNumber++;
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] open #{} of this session: screen={} effect={} source={}",
                    openNumber, screenKey, effect, source);
        }
    }

    public static int openCount() {
        return openNumber;
    }

    public static void openStep(String name, long startNanos) {
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] open #{} {}: {} ms", openNumber, name, ms(now() - startNanos));
        }
    }

    public static void openValue(String name, Object value) {
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] open #{} {}: {}", openNumber, name, value);
        }
    }

    public static int closeCount() {
        return closeNumber;
    }

    public static void step(String name, long startNanos) {
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] close #{} {}: {} ms", closeNumber, name, ms(now() - startNanos));
        }
    }

    public static void value(String name, Object value) {
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] close #{} {}: {}", closeNumber, name, value);
        }
    }

    public static void closeEffectDrawn() {
        if (closeRequested == 0) {
            return;
        }
        lastCloseLatencyMs = (now() - closeRequested) / 1e6;
        closeRequested = 0;
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] close #{} close.latency (setScreen(null) -> first frame of the effect, frame +{}): {} ms",
                    closeNumber, frameIndex, String.format(Locale.ROOT, "%.3f", lastCloseLatencyMs));
        }
    }

    public static double lastCloseLatencyMs() {
        return lastCloseLatencyMs;
    }

    public static void frameStart() {
        long t = now();
        if (framesLeft > 0 && enabled() && lastFrameStart != 0) {
            ShatterMod.LOGGER.info("[ShatterProfile] close #{} frame +{} interval: {} ms",
                    closeNumber, frameIndex, ms(t - lastFrameStart));
        }
        frameStart = t;
        lastFrameStart = t;
    }

    public static void frameEnd() {
        if (framesLeft <= 0) {
            return;
        }
        if (enabled()) {
            ShatterMod.LOGGER.info("[ShatterProfile] close #{} frame +{} GameRenderer.render: {} ms",
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
