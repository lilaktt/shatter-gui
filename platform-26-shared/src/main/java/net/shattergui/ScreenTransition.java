package net.shattergui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.CloseWatchdog;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.FlyEffect;
import net.shattergui.effect.GlassEffect;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.effect.modifier.EdgeTransition;
import net.shattergui.fly.FlyController;
import net.shattergui.fly.FlySource;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

import java.util.List;

public final class ScreenTransition {
    private ScreenTransition() {
    }

    public static final class State {
        Screen closing;
        boolean opening;
        boolean spatial;
        long start;
    }

    public static State before(Screen current, Screen newScreen) {
        State state = new State();
        if (!ShatterConfig.get().enabled) {
            return state;
        }
        state.start = ShatterProfiler.now();
        if (newScreen == null && current != null) {
            CloseWatchdog.begin(current.getClass().getSimpleName());
        }
        if (current != null && newScreen != current) {
            FlyController.endOpening(newScreen == null ? "closed" : "replaced by " + newScreen.getClass().getSimpleName());
            CloseWatchdog.step("end the opening of the screen", state.start);
        }
        state.closing = newScreen == null ? current : null;
        state.opening = newScreen != null && current == null;
        state.spatial = newScreen == null && SpatialGuiCompat.isShowing(current);
        return state;
    }

    public static void after(Screen current, Screen newScreen, State state, ClientLevel level, LocalPlayer player) {
        if (current != null) {
            net.shattergui.fly.OpenBackdrop.cancelClose();
        }
        if (state.opening && current == newScreen && level != null && player != null) {
            FlyController.onScreenOpened(newScreen);
            return;
        }
        Screen old = state.closing;
        if (old == null || current != null) {
            return;
        }
        long stepStart = ShatterProfiler.now();
        FlySource flySource = FlyController.onScreenClosed();
        stepStart = CloseWatchdog.step("stop the fly-in, find the source", stepStart);
        if (level == null || player == null || player.isDeadOrDying()) {
            return;
        }
        ShatterConfig config = ShatterConfig.get();
        if (!config.enabled) {
            return;
        }
        long triggerStart = ShatterProfiler.now();
        List<String> keys = ScreenRules.keys(old);
        ScreenRules.remember(old, keys);
        if (!ScreenRules.shouldShatter(old, keys, config)) {
            return;
        }
        ShatterEffect effect = ScreenRules.effectFor(keys, config, FlyController.closedSourceKey());
        FlyTarget target = flySource != null ? FlyTarget.point(flySource.point()) : null;
        boolean flyFallback = false;
        if (effect.base() instanceof FlyEffect && flySource == null) {
            if (!FlyController.hadSource()) {
                ParamValues params = effect.prepare(config.values());
                target = EdgeTransition.allows(old) ? FlyTarget.edge(EdgeTransition.pick(
                        params.s(Params.FLY_EDGE_DIRECTION), player.getRandom(), !params.reduceMotion())) : null;
            } else {
                effect = ShatterEffects.resolve(GlassEffect.ID);
                flyFallback = true;
            }
        }
        stepStart = CloseWatchdog.step("pick the effect", stepStart);
        ShatterProfiler.beginClose(keys.getFirst(), effect.id(), state.start);
        ShatterProfiler.value("spatial.showingThisScreen", state.spatial);
        if (target != null) {
            ShatterProfiler.value("fly.target", flySource != null ? flySource.describe() : target.describe());
        } else if (effect.base() instanceof FlyEffect) {
            ShatterProfiler.value("fly.target", "none: fades in place (screen excluded from edge flights)");
        } else if (flyFallback) {
            ShatterProfiler.value("fly.fallback to glass", FlyController.missingReason());
        }
        if (!ShardManager.admit(ScreenCapture.busy() ? 1 : 0, "close of " + keys.getFirst())) {
            CloseWatchdog.endCall(state.start);
            return;
        }
        stepStart = ShatterProfiler.now();
        SpatialGuiCompat.startClose(old, state.spatial, effect, target, ParamValues.current(), false);
        if (!state.spatial) {
            net.shattergui.fly.OpenBackdrop.startClose(old, ParamValues.current());
        }
        CloseWatchdog.step("start the close (queue the capture or take the Spatial GUI plane)", stepStart);
        CloseWatchdog.endCall(state.start);
        ShatterProfiler.step("trigger (pick effect + snapshot camera)", triggerStart);
        ShatterProfiler.step("setScreen(null) total incl. vanilla", state.start);
    }
}
