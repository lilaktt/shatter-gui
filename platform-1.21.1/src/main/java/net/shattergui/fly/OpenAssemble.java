package net.shattergui.fly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * A screen that opens by gathering: its picture is captured on the first frame and an effect plays backwards, its
 * pieces flying together into the interface. Until they have landed the real screen is not drawn and ignores the
 * mouse; then it simply appears where the pieces are.
 *
 * <p>On a Spatial GUI plane the screen is still extracted (Spatial GUI draws it into its own texture, which is what
 * the pieces are cut from); the plane is drawn fully transparent instead ({@link #spatialAlpha}) and appears where the
 * pieces have landed.
 *
 * <p>Never stuck: if the capture or the effect does not start within {@link #START_GRACE_TICKS}, or the effect runs
 * longer than its own length plus {@link #END_GRACE_TICKS}, the screen is shown anyway; {@link #MAX_MS} of real time is
 * the last resort. Closing or replacing the screen ends it too. The limits count client ticks, like the wave itself: a
 * wall-clock limit ran out in the middle of the wave whenever ticks came slower than 20 a second (a lag spike, the
 * effect recorded ahead of time on its first tick), and the real screen then covered the pieces. Ticks while the game
 * is paused count too (see {@link net.shattergui.shatter.EffectClock}): the pause menu pauses the game from its first
 * tick, and its opening stood still until {@link #MAX_MS}.
 */
public final class OpenAssemble {
    private static final int START_GRACE_TICKS = 10;
    private static final int END_GRACE_TICKS = 6;
    private static final long MAX_MS = 5_000;
    private static Screen screen;
    private static boolean spatial;
    /** Client ticks since the game started, paused or not. */
    private static long ticks;
    private static long deadline;
    private static long hardDeadline;
    /** Length of the running gathering wave in ticks, 0 before it started (tests time their frames by it). */
    private static int waveTicks;
    /** For the profile log: the screen whose opening is timed, when it started, when its real screen showed. */
    private static Screen timed;
    private static long openNanos;
    private static long openTick;
    private static boolean shownLogged;
    private static boolean inputLogged;

    private OpenAssemble() {
    }

    /** @param onSpatialPlane Spatial GUI shows the screen: its plane is hidden instead of the flat GUI */
    public static void start(Screen opened, boolean onSpatialPlane) {
        screen = opened;
        spatial = onSpatialPlane;
        waveTicks = 0;
        deadline = ticks + START_GRACE_TICKS;
        hardDeadline = System.currentTimeMillis() + MAX_MS;
        startTiming(opened, "gathering");
    }

    /** The fly-out opening started (timed the same way; its end is when FlyOpen stops). */
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

    /** The real screen is drawn again (the gathering is over or gave up). */
    private static void shown(String how) {
        if (timed != null && !shownLogged) {
            shownLogged = true;
            net.shattergui.debug.ShatterProfiler.openValue("open.timing.shown", String.format(java.util.Locale.ROOT,
                    "%.1f ms, %d ticks after the start (%s)", (System.nanoTime() - openNanos) / 1e6, ticks - openTick, how));
        }
    }

    /**
     * The screen takes the mouse again: the moment {@code FlyController.busy} turns false (nothing hides it and it does
     * not fly any more). Checked every tick; logged once per opening.
     */
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

    /** END_CLIENT_TICK: counts ticks the opening waves advance in (also while the game is paused). */
    public static void tick() {
        // First, so an opening that ended in this tick's waves is logged in the same tick as it was shown.
        checkUnlocked();
        ticks++;
    }

    /** The backwards wave exists: wait for its whole length. */
    public static void waveStarted(int length) {
        if (screen != null) {
            waveTicks = length;
            deadline = ticks + length + END_GRACE_TICKS;
        }
    }

    /** The backwards wave has ended (or was removed): show the screen. */
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

    /** True while {@code s} is still being gathered: it is not drawn and ignores the mouse. */
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

    /** True while the flat GUI of {@code s} must not be drawn (on a Spatial GUI plane it is drawn, the plane hidden). */
    public static boolean hidesFlat(Screen s) {
        return !spatial && hides(s);
    }

    /** Opacity of Spatial GUI's plane: 0 while its screen is being gathered (multiplies its own). */
    public static float spatialAlpha() {
        return spatial && screen != null && hides(screen) ? 0f : 1f;
    }

    /** Whether the gathering runs on a Spatial GUI plane. */
    public static boolean onSpatialPlane() {
        return spatial && screen != null;
    }

    /** Length of the gathering wave of the screen being gathered, 0 until it has started. */
    public static int waveTicks() {
        return screen != null ? waveTicks : 0;
    }

    public static boolean active() {
        return screen != null;
    }
}
