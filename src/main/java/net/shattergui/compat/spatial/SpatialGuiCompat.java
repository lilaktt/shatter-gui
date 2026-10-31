package net.shattergui.compat.spatial;

import net.minecraft.client.gui.screens.Screen;
import net.shattergui.config.ShatterConfig;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.ShatterGuiClient;

/**
 * Optional integration with Spatial GUI (https://github.com/tastytrash/Spatial-GUI, MIT): when it shows the closing
 * screen as a 3D plane, that plane shatters in place instead of a flat copy, and its own close animation is skipped.
 *
 * <p>This class never touches Spatial GUI classes, so it is safe to load without the mod; {@link SpatialGuiBridge} does
 * the actual work and is only reached when a supported version is installed. Any failure logs one warning and turns
 * the integration off for the session, after which the mod behaves as if Spatial GUI were absent.
 */
public final class SpatialGuiCompat {
    private static boolean failed;

    private SpatialGuiCompat() {
    }

    /** True if Spatial GUI is currently drawing {@code screen} as its plane. Call before the screen is removed. */
    public static boolean isShowing(Screen screen) {
        if (!active() || screen == null) {
            return false;
        }
        try {
            return SpatialGuiBridge.isShowing(screen);
        } catch (Throwable t) {
            fail("checking the shown screen", t);
            return false;
        }
    }

    /**
     * A screen just closed: its effect starts. The one place for every way an effect is chosen (general, per screen,
     * thematic, custom, the cycle key, the editor's preview): if Spatial GUI was showing the screen its plane shatters in
     * place — or, if that is not possible, the plane is still hidden (its own close animation never plays behind ours)
     * and the effect plays on a flat copy.
     */
    public static void startClose(Screen screen, boolean onPlane, ShatterEffect effect, FlyTarget target,
                                  ParamValues params, boolean preview) {
        if (onPlane && tryShatter(screen, effect, target, params, preview)) {
            return;
        }
        if (onPlane && active()) {
            try {
                SpatialGuiBridge.hideClosingPlane();
            } catch (Throwable t) {
                fail("hiding its plane", t);
            }
        }
        net.shattergui.capture.ScreenCapture.requestCapture(screen, effect, target, params, preview);
    }

    /**
     * Takes over the screen that was just closed: in the next frame Spatial GUI's plane shatters in place and its close
     * animation is cancelled (see {@link #completePending}). Returns false if that is not possible; the caller then
     * falls back to the flat capture.
     */
    public static boolean tryShatter(Screen screen, ShatterEffect effect, FlyTarget target) {
        return tryShatter(screen, effect, target, ParamValues.current(), false);
    }

    /** As above with given settings; {@code preview}: the editor's preview. */
    public static boolean tryShatter(Screen screen, ShatterEffect effect, FlyTarget target, ParamValues params,
                                     boolean preview) {
        if (!active()) {
            return false;
        }
        try {
            return SpatialGuiBridge.queue(screen, effect, target, params, preview);
        } catch (Throwable t) {
            fail("taking over its plane", t);
            return false;
        }
    }

    /**
     * For the screen that just opened (and that {@link #isShowing}): skips Spatial GUI's own open animation, so the
     * fly-in can move its plane instead. Returns false if that is not possible; the caller then uses the flat fly-in.
     */
    public static boolean cancelOpenAnimation() {
        if (!active()) {
            return false;
        }
        try {
            return SpatialGuiBridge.cancelOpenAnimation();
        } catch (Throwable t) {
            fail("cancelling its open animation", t);
            return false;
        }
    }

    /**
     * The screen that just opened (and that {@link #isShowing}) gathers on Spatial GUI's plane: see
     * {@code SpatialGuiBridge#queueOpen}. Returns false if that is not possible.
     */
    public static boolean tryGather(Screen screen, ShatterEffect effect, FlyTarget target, ParamValues params) {
        if (!active()) {
            return false;
        }
        try {
            return SpatialGuiBridge.queueOpen(screen, effect, target, params);
        } catch (Throwable t) {
            fail("gathering on its plane", t);
            return false;
        }
    }

    /** Called every frame after the GUI is drawn; starts a gathering queued by {@link #tryGather}. */
    public static void completeOpenPending() {
        if (!active()) {
            return;
        }
        try {
            SpatialGuiBridge.completeOpen();
        } catch (Throwable t) {
            fail("gathering on its plane", t);
        }
    }

    /** Called every frame after the level is drawn; finishes a close queued by {@link #tryShatter}. */
    public static void completePending() {
        if (!active()) {
            return;
        }
        try {
            SpatialGuiBridge.complete();
        } catch (Throwable t) {
            fail("shattering its plane", t);
        }
    }

    private static boolean active() {
        ShatterConfig config = ShatterConfig.get();
        return config.enabled && config.spatialGuiIntegration && !failed && SpatialGuiDetector.isSupportedVersionInstalled();
    }

    /** The opened screen closed or was replaced before its gathering started: forget it. */
    public static void cancelPendingOpen() {
        if (SpatialGuiDetector.isSupportedVersionInstalled() && !failed) {
            try {
                SpatialGuiBridge.cancelPendingOpen();
            } catch (Throwable t) {
                fail("cancelling a pending opening", t);
            }
        }
    }

    /** The mod was switched off: forget a close that has not reached its frame yet. */
    public static void cancelPending() {
        if (SpatialGuiDetector.isSupportedVersionInstalled() && !failed) {
            try {
                SpatialGuiBridge.cancelPending();
            } catch (Throwable t) {
                fail("cancelling a pending close", t);
            }
        }
    }

    private static void fail(String what, Throwable t) {
        failed = true;
        ShatterGuiClient.LOGGER.warn("Spatial GUI integration disabled for this session (error while {}); "
                + "closing screens falls back to the normal effect", what, t);
    }
}
