package net.shattergui.compat.spatial;

import net.minecraft.client.gui.screens.Screen;
import net.shattergui.config.ShatterConfig;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.ShatterGuiClient;

public final class SpatialGuiCompat {
    private static boolean failed;

    private SpatialGuiCompat() {
    }

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

    public static boolean tryShatter(Screen screen, ShatterEffect effect, FlyTarget target) {
        return tryShatter(screen, effect, target, ParamValues.current(), false);
    }

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

    public static org.joml.Vector3d planeCenterNow() {
        if (!active()) {
            return null;
        }
        net.shattergui.shatter.PlaneBasis plane = SpatialGuiBridge.planeNow();
        return plane == null ? null : plane.center();
    }

    public static void cancelPendingOpen() {
        if (SpatialGuiDetector.isSupportedVersionInstalled() && !failed) {
            try {
                SpatialGuiBridge.cancelPendingOpen();
            } catch (Throwable t) {
                fail("cancelling a pending opening", t);
            }
        }
    }

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
