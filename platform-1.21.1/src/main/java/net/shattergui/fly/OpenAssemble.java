package net.shattergui.fly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class OpenAssemble {
    private static final int START_GRACE_TICKS = 10;
    private static final int END_GRACE_TICKS = 6;
    private static final long MAX_MS = 5_000;
    private static Screen screen;
    private static boolean spatial;
    private static long ticks;
    private static long deadline;
    private static long hardDeadline;
    private static int waveTicks;
    private static Screen timed;
    private static long openNanos;
    private static long openTick;
    private static boolean shownLogged;
    private static boolean inputLogged;

    private OpenAssemble() {
    }

    public static void start(Screen opened, boolean onSpatialPlane) {
        screen = opened;
        spatial = onSpatialPlane;
        waveTicks = 0;
        deadline = ticks + START_GRACE_TICKS;
        hardDeadline = System.currentTimeMillis() + MAX_MS;
        startTiming(opened, "gathering");
    }

    public static void flyStarted(Screen opened, int length) {
        startTiming(opened, "fly-out, " + length + " ticks");
    }

    private static void startTiming(Screen opened, String what) {
        timed = opened;
        openNanos = System.nanoTime();
        openTick = ticks;
        shownLogged = false;
        inputLogged = false;
        net.shattergui.debug.ShatterProfiler.openValue("open.timing.start", what);
    }

    private static void shown(String how) {
        if (timed != null && !shownLogged) {
            shownLogged = true;
            net.shattergui.debug.ShatterProfiler.openValue("open.timing.shown", String.format(java.util.Locale.ROOT,
                    "%.1f ms, %d ticks after the start (%s)", (System.nanoTime() - openNanos) / 1e6, ticks - openTick, how));
        }
    }

    private static void checkUnlocked() {
        Screen s = timed;
        if (s != null && !inputLogged && !FlyOpen.isAnimating(s) && !hides(s)) {
            inputLogged = true;
            if (!shownLogged) {
                shown("fly-out over");
            }
            net.shattergui.debug.ShatterProfiler.openValue("open.timing.inputUnlocked", String.format(java.util.Locale.ROOT,
                    "%.1f ms, %d ticks after the start", (System.nanoTime() - openNanos) / 1e6, ticks - openTick));
            timed = null;
        }
    }

    public static void tick() {
        checkUnlocked();
        ticks++;
    }

    public static void waveStarted(int length) {
        if (screen != null) {
            waveTicks = length;
            deadline = ticks + length + END_GRACE_TICKS;
        }
    }

    public static void waveDone() {
        if (screen != null) {
            shown("pieces landed");
        }
        screen = null;
    }

    public static void stop() {
        screen = null;
        timed = null;
    }

    public static boolean hides(Screen s) {
        if (screen == null || s != screen) {
            return false;
        }
        if (ticks > deadline || System.currentTimeMillis() > hardDeadline || Minecraft.getInstance().screen != screen) {
            shown(ticks > deadline ? "deadline" : "screen changed");
            screen = null;
            return false;
        }
        return true;
    }

    public static boolean hidesFlat(Screen s) {
        return !spatial && hides(s);
    }

    public static float spatialAlpha() {
        return spatial && screen != null && hides(screen) ? 0f : 1f;
    }

    public static boolean onSpatialPlane() {
        return spatial && screen != null;
    }

    public static int waveTicks() {
        return screen != null ? waveTicks : 0;
    }

    public static boolean active() {
        return screen != null;
    }
}
