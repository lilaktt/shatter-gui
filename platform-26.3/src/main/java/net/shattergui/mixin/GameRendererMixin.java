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

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;
    @Shadow
    @Final
    private FogRenderer fogRenderer;

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private Matrix4f shatterGui$levelProjection(Matrix4f projection) {
        OnTopPass.levelProjection(projection);
        OnTopPass.cleanProjection(gameRenderState.levelRenderState.cameraRenderState.projectionMatrix);
        OnTopPass.cleanViewRotation(gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix);
        return projection;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJFILnet/minecraft/world/phys/Vec3;Z)V"),
            index = 5)
    private int shatterGui$fadeBlur(int radius) {
        return Math.round(net.shattergui.fly.OpenBackdrop.blurRadius(radius));
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"))
    private void shatterGui$afterLevel(CallbackInfo ci) {
        FrameHooks.afterLevel(fogRenderer.getBuffer(FogRenderer.FogMode.NONE),
                gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix);
    }

    @Inject(method = "render3dHud", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"))
    private void shatterGui$beforeHand(CameraRenderState cameraState, PlayerRenderState playerState,
                                       OptionsRenderState optionsState, boolean consistentDepthRequired, CallbackInfo ci) {
        FrameHooks.beforeHand(fogRenderer.getBuffer(FogRenderer.FogMode.NONE), cameraState.viewRotationMatrix);
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
