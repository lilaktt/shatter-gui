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

/**
 * Drives the one-frame capture of a closing screen ({@link ScreenCapture}) and, on the same path, the pictures of the
 * editor's real preview ({@link RealPreview}). 1.21.1's {@code GameRenderer#render}: level, post effect, main target
 * bound, GUI projection, {@code new GuiGraphics}, then {@code Gui#render} (the HUD) and the screen — the capture goes
 * in right before the HUD (javap). The shards themselves are drawn at the end of the level
 * ({@code WorldRenderEvents.END}, see ShardRenderer), so this mixin has nothing to do with them.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void shatterGui$beforeHud(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        long start = System.nanoTime();
        SpatialGuiCompat.completePending();
        start = CloseWatchdog.step("Spatial GUI: copy its plane and spawn the pieces", start);
        // The partial tick vanilla draws screens with (GameRenderer#render: Screen#renderWithTooltip).
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
