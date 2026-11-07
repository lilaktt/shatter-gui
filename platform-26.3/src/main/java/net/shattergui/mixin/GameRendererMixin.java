package net.shattergui.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
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

/** The frame's hooks in 26.3 (extraction and drawing are separate calls here, the drawing takes no arguments). What they do is in {@link FrameHooks}. */
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
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private Matrix4f shatterGui$levelProjection(Matrix4f projection) {
        OnTopPass.levelProjection(projection);
        OnTopPass.cleanProjection(gameRenderState.levelRenderState.cameraRenderState.projectionMatrix);
        OnTopPass.levelViewRotation(gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix);
        return projection;
    }

    /** The menu blur radius of this frame: faded with an opening or closing screen (OpenBackdrop). */
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJFILnet/minecraft/world/phys/Vec3;Z)V"),
            index = 5)
    private int shatterGui$fadeBlur(int radius) {
        return Math.round(net.shattergui.fly.OpenBackdrop.blurRadius(radius));
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"))
    private void shatterGui$afterLevel(CallbackInfo ci) {
        FrameHooks.afterLevel(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
    }

    /**
     * Right before vanilla clears the depth for the hand (javap: the one clear in render3dHud). The main depth still holds
     * the world; with post effects vanilla clears its separate HUD depth instead and the main one keeps the world anyway.
     */
    @Inject(method = "render3dHud", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"))
    private void shatterGui$beforeHand(CameraRenderState cameraState, PlayerRenderState playerState,
                                       OptionsRenderState optionsState, boolean consistentDepthRequired, CallbackInfo ci) {
        FrameHooks.beforeHand(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void shatterGui$frameStart(CallbackInfo ci) {
        FrameHooks.frameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shatterGui$frameEnd(CallbackInfo ci) {
        FrameHooks.frameEnd();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void shatterGui$beforeGuiRender(CallbackInfo ci) {
        FrameHooks.beforeGuiRender();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
    private void shatterGui$afterGuiRender(CallbackInfo ci) {
        FrameHooks.afterGuiRender(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
    }
}
