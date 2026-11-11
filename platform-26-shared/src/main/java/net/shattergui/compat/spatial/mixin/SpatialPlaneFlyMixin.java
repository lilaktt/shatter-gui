package net.shattergui.compat.spatial.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"org.tastytrash.spatialGUI.render.WorldScreenRenderer", "org.tastytrash.spatialGUI.render.InventoryRenderer"},
        remap = false)
public abstract class SpatialPlaneFlyMixin {
    @Shadow(remap = false)
    @Final
    private static PoseStack WORLD_POSE_STACK;

    @Inject(method = "computeScreenPose", at = @At("RETURN"), remap = false)
    private void shatterGui$flyIn(PoseStack drawStack, boolean firstPerson, CallbackInfoReturnable<Object> cir) {
        Matrix4f plane = WORLD_POSE_STACK.last().pose();
        Matrix4f fly = FlyOpen.spatialMatrix(plane.getTranslation(new Vector3f()));
        if (fly == null) {
            return;
        }
        if (drawStack != null) {
            drawStack.last().pose().mul(new Matrix4f(plane).invert().mul(fly).mul(plane));
        }
        plane.mulLocal(fly);
    }

    @Inject(method = "getFadeAlpha", at = @At("RETURN"), cancellable = true, remap = false)
    private void shatterGui$fadeIn(CallbackInfoReturnable<Float> cir) {
        float alpha = FlyOpen.spatialAlpha() * OpenAssemble.spatialAlpha();
        if (alpha < 1f) {
            cir.setReturnValue(cir.getReturnValueF() * alpha);
        }
        net.shattergui.debug.FrameAudit.plane(cir.getReturnValueF());
    }
}
