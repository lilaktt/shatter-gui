package net.shattergui.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.shattergui.ScreenRules;
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
import net.shattergui.ui.preview.RealPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public Screen screen;
    @Shadow
    public ClientLevel level;
    @Shadow
    public LocalPlayer player;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void shatterGui$blockFromPreview(Screen newScreen, CallbackInfo ci) {
        if (RealPreview.blocksSetScreen(newScreen)) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void shatterGui$rememberOldScreen(Screen newScreen, CallbackInfo ci,
                                              @Share("closing") LocalRef<Screen> closing,
                                              @Share("opening") LocalBooleanRef opening,
                                              @Share("spatial") LocalBooleanRef spatial,
                                              @Share("start") LocalLongRef start) {
        if (!ShatterConfig.get().enabled) {
            closing.set(null);
            opening.set(false);
            spatial.set(false);
            return;
        }
        start.set(ShatterProfiler.now());
        if (newScreen == null && this.screen != null) {
            CloseWatchdog.begin(this.screen.getClass().getSimpleName());
        }
        if (this.screen != null && newScreen != this.screen) {
            FlyController.endOpening(newScreen == null ? "closed" : "replaced by " + newScreen.getClass().getSimpleName());
            CloseWatchdog.step("end the opening of the screen", start.get());
        }
        closing.set(newScreen == null ? this.screen : null);
        opening.set(newScreen != null && this.screen == null);
        spatial.set(newScreen == null && SpatialGuiCompat.isShowing(this.screen));
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void shatterGui$onScreenClosed(Screen newScreen, CallbackInfo ci,
                                           @Share("closing") LocalRef<Screen> closing,
                                           @Share("opening") LocalBooleanRef opening,
                                           @Share("spatial") LocalBooleanRef spatial,
                                           @Share("start") LocalLongRef start) {
        if (opening.get() && this.screen == newScreen && level != null && player != null) {
            FlyController.onScreenOpened(newScreen);
            return;
        }
        Screen old = closing.get();
        if (old == null || this.screen != null) {
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
        ShatterProfiler.beginClose(keys.getFirst(), effect.id(), start.get());
        ShatterProfiler.value("spatial.showingThisScreen", spatial.get());
        if (target != null) {
            ShatterProfiler.value("fly.target", flySource != null ? flySource.describe() : target.describe());
        } else if (effect.base() instanceof FlyEffect) {
            ShatterProfiler.value("fly.target", "none: fades in place (screen excluded from edge flights)");
        } else if (flyFallback) {
            ShatterProfiler.value("fly.fallback to glass", FlyController.missingReason());
        }
        if (!ShardManager.admit(ScreenCapture.busy() ? 1 : 0, "close of " + keys.getFirst())) {
            CloseWatchdog.endCall(start.get());
            return;
        }
        stepStart = ShatterProfiler.now();
        SpatialGuiCompat.startClose(old, spatial.get(), effect, target, ParamValues.current(), false);
        CloseWatchdog.step("start the close (queue the capture or take the Spatial GUI plane)", stepStart);
        CloseWatchdog.endCall(start.get());
        ShatterProfiler.step("trigger (pick effect + snapshot camera)", triggerStart);
        ShatterProfiler.step("setScreen(null) total incl. vanilla", start.get());
    }
}
