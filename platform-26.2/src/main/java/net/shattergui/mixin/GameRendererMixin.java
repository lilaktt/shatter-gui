package net.shattergui.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.shattergui.render.FrameHooks;
import net.shattergui.render.OnTopPass;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The frame's hooks in 26.2. What they do is in {@link FrameHooks}. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;
    @Shadow
    @Final
    private FogRenderer fogRenderer;

    /** The level's projection, view bobbing included, and rotation, for the effects drawn over the world. */
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private Matrix4f shatterGui$levelProjection(Matrix4f projection) {
        OnTopPass.levelProjection(projection);
        OnTopPass.cleanProjection(gameRenderState.levelRenderState.cameraRenderState.projectionMatrix);
        // Before the level render: without a shader pack's view bobbing (it is put into this matrix during the render).
        OnTopPass.cleanViewRotation(gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix);
        return projection;
    }

    /** The menu blur radius of this frame: faded with an opening or closing screen (OpenBackdrop). */
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V"),
            index = 5)
    private int shatterGui$fadeBlur(int radius) {
        return Math.round(net.shattergui.fly.OpenBackdrop.blurRadius(radius));
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void shatterGui$afterLevel(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FrameHooks.afterLevel(fogRenderer.getBuffer(FogRenderer.FogMode.NONE),
                gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix);
    }

    /** Right before vanilla clears the depth for the hand (javap: the one clear in renderLevel): it still holds the world. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void shatterGui$beforeHand(DeltaTracker deltaTracker, CallbackInfo ci) {
        FrameHooks.beforeHand(fogRenderer.getBuffer(FogRenderer.FogMode.NONE),
                gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void shatterGui$frameStart(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FrameHooks.frameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shatterGui$frameEnd(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FrameHooks.frameEnd();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void shatterGui$beforeGuiRender(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FrameHooks.beforeGuiRender();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
    private void shatterGui$afterGuiRender(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FrameHooks.afterGuiRender(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
    }
}
