package net.shattergui.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.debug.CloseWatchdog;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.render.OnTopPass;
import net.shattergui.ui.preview.RealPreview;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Drives the one-frame capture of a closing screen ({@link ScreenCapture}) and, on the same path, the pictures of the
 * editor's real preview ({@link RealPreview}).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;
    @Shadow
    @Final
    private FogRenderer fogRenderer;

    /** The level's projection, view bobbing included, for the effects drawn over the world. */
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private Matrix4f shatterGui$levelProjection(Matrix4f projection) {
        OnTopPass.levelProjection(projection);
        return projection;
    }

    /** The camera rotation the level is drawn with (its model-view). */
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"),
            index = 4)
    private Matrix4fc shatterGui$levelViewRotation(Matrix4fc viewRotation) {
        OnTopPass.levelViewRotation(viewRotation);
        return viewRotation;
    }

    /** Effects over the world, under the player's hand (vanilla has just cleared the depth for the hand). */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"))
    private void shatterGui$beforeHand(DeltaTracker deltaTracker, CallbackInfo ci) {
        long start = System.nanoTime();
        OnTopPass.draw(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        CloseWatchdog.step("draw the effects over the world", start);
    }

    @Inject(method = "extractGui", at = @At("HEAD"), cancellable = true)
    private void shatterGui$extractClosingScreen(DeltaTracker deltaTracker, boolean a, boolean b, CallbackInfo ci) {
        long start = System.nanoTime();
        boolean captured = ScreenCapture.extractPendingScreen(gameRenderState.guiRenderState, deltaTracker.getGameTimeDeltaTicks());
        CloseWatchdog.step("capture: draw the closed screen", start);
        if (captured || RealPreview.extract(gameRenderState.guiRenderState, deltaTracker.getGameTimeDeltaTicks())) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void shatterGui$frameStart(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ShatterProfiler.frameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shatterGui$frameEnd(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ShatterProfiler.frameEnd();
        CloseWatchdog.frameEnd();
        net.shattergui.debug.FrameAudit.endFrame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void shatterGui$beforeGuiRender(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        long start = System.nanoTime();
        SpatialGuiCompat.completePending();
        start = CloseWatchdog.step("Spatial GUI: copy its plane and spawn the pieces", start);
        // Effects over the world if the hand was not drawn this frame (else drawn already, under it); before the capture
        // backs the frame up (the restored frame has them, the GUI covers them).
        OnTopPass.draw(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        start = CloseWatchdog.step("draw the effects over the world", start);
        ScreenCapture.beforeGuiRender();
        CloseWatchdog.step("capture: back up and clear the frame", start);
        RealPreview.beforeGuiRender();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.AFTER))
    private void shatterGui$afterGuiRender(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        long start = System.nanoTime();
        ScreenCapture.afterGuiRender();
        CloseWatchdog.step("capture: copy to the texture pool and spawn the pieces", start);
        RealPreview.afterGuiRender();
        SpatialGuiCompat.completeOpenPending();
    }
}
