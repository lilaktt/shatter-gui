package net.shattergui.compat.spatial;

import net.minecraft.client.gui.screens.Screen;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;

/**
 * Spatial GUI on 1.21.1: not integrated yet, so every answer is "Spatial GUI is not showing this screen" and closes use
 * the flat capture. Same methods as on 26.1, so the callers are the same.
 */
public final class SpatialGuiCompat {
    private SpatialGuiCompat() {
    }

    public static boolean isShowing(Screen screen) {
        return false;
    }

    public static void startClose(Screen screen, boolean onPlane, ShatterEffect effect, FlyTarget target,
                                  ParamValues params, boolean preview) {
        ScreenCapture.requestCapture(screen, effect, target, params, preview);
    }

    public static boolean tryShatter(Screen screen, ShatterEffect effect, FlyTarget target) {
        return false;
    }

    public static boolean cancelOpenAnimation() {
        return false;
    }

    public static boolean tryGather(Screen screen, ShatterEffect effect, FlyTarget target, ParamValues params) {
        return false;
    }

    public static void completeOpenPending() {
    }

    public static void completePending() {
    }

    public static void cancelPendingOpen() {
    }

    public static void cancelPending() {
    }
}
