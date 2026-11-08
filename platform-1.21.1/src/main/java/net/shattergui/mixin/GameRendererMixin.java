package net.shattergui.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.debug.CloseWatchdog;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.ui.preview.RealPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void shatterGui$beforeHud(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        long start = System.nanoTime();
        SpatialGuiCompat.completePending();
        start = CloseWatchdog.step("Spatial GUI: copy its plane and spawn the pieces", start);
        ScreenCapture.captureBeforeHud(deltaTracker.getRealtimeDeltaTicks());
        CloseWatchdog.step("capture: draw the closed screen into its texture and spawn the pieces", start);
        RealPreview.beforeHud(deltaTracker.getGameTimeDeltaTicks());
        SpatialGuiCompat.completeOpenPending();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void shatterGui$frameStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ShatterProfiler.frameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shatterGui$frameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ShatterProfiler.frameEnd();
        CloseWatchdog.frameEnd();
        net.shattergui.debug.FrameAudit.endFrame();
    }
}
