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

/**
 * What happens around the game's change of screen: a screen being opened over the world or closed back to it. The
 * version's mixin calls {@link #before} and {@link #after} around {@code setScreen} (on {@code Minecraft} in 26.1, on
 * {@code Gui} since 26.2). The decision is made after the call, because vanilla may turn {@code setScreen(null)} into
 * another screen (title screen without a world, death screen, a restored chat screen).
 */
public final class ScreenTransition {
    private ScreenTransition() {
    }

    /** What {@link #before} found out for {@link #after}, per call (setScreen can be re-entered). */
    public static final class State {
        Screen closing;
        boolean opening;
        boolean spatial;
        long start;
    }

    /** {@code current}: the screen before the change. */
    public static State before(Screen current, Screen newScreen) {
        State state = new State();
        if (!ShatterConfig.get().enabled) {
            // Switched off: nothing else is looked at, Spatial GUI plays its own animations.
            return state;
        }
        state.start = ShatterProfiler.now();
        if (newScreen == null && current != null) {
            CloseWatchdog.begin(current.getClass().getSimpleName());
        }
        if (current != null && newScreen != current) {
            // Closed or replaced: whatever opening it still had ends now (before Spatial GUI unhooks it).
            FlyController.endOpening(newScreen == null ? "closed" : "replaced by " + newScreen.getClass().getSimpleName());
            CloseWatchdog.step("end the opening of the screen", state.start);
        }
        state.closing = newScreen == null ? current : null;
        state.opening = newScreen != null && current == null;
        // Must be asked before the screen is removed: Spatial GUI unhooks it during this call.
        state.spatial = newScreen == null && SpatialGuiCompat.isShowing(current);
        return state;
    }

    /** {@code current}: the screen after the change. */
    public static void after(Screen current, Screen newScreen, State state, ClientLevel level, LocalPlayer player) {
        if (current != null) {
            // A screen is up again: it draws its own background (fading in if it opens with an effect).
            net.shattergui.fly.OpenBackdrop.cancelClose();
        }
        // A screen opened over the world (Spatial GUI has hooked it by now, in ScreenEvents.BEFORE_INIT).
        if (state.opening && current == newScreen && level != null && player != null) {
            FlyController.onScreenOpened(newScreen);
            return;
        }
        Screen old = state.closing;
        // Shatter only a real close into a loaded world.
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
        // Fly back into the container; a screen opened without one flies off an edge (chat, signs and books fade
        // in place instead); a container that is gone (block broken, entity dead) closes with glass.
        // Any effect may use the container (vortex flies into it); only fly uses the edge.
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
            // skipNew: the screen just closes.
            CloseWatchdog.endCall(state.start);
            return;
        }
        // Spatial GUI's own plane if it was showing this screen, otherwise (or if that fails) the flat capture.
        stepStart = ShatterProfiler.now();
        SpatialGuiCompat.startClose(old, state.spatial, effect, target, ParamValues.current(), false);
        if (!state.spatial) {
            // The darkened world behind the flat screen fades out with the effect instead of vanishing at once.
            net.shattergui.fly.OpenBackdrop.startClose(old, ParamValues.current());
        }
        CloseWatchdog.step("start the close (queue the capture or take the Spatial GUI plane)", stepStart);
        CloseWatchdog.endCall(state.start);
        ShatterProfiler.step("trigger (pick effect + snapshot camera)", triggerStart);
        // Includes vanilla work: Screen#removed, closing the container, sending the close packet.
        ShatterProfiler.step("setScreen(null) total incl. vanilla", state.start);
    }
}
