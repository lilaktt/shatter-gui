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

/**
 * Flies Spatial GUI's plane out of the container while a screen opens (see {@link FlyOpen}), and hides it while the
 * screen is gathered by pieces ({@link OpenAssemble}). Same private members in
 * 1.8 and 1.9; targeted by name so nothing here links against Spatial GUI; applied only through
 * {@code SpatialGuiMixinPlugin}.
 *
 * <p>{@code computeScreenPose(drawStack, firstPerson)} puts the plane's transform P (camera-relative, world axes) into
 * {@code WORLD_POSE_STACK} (from which the plane basis for the mouse ray is taken) and the same P after the camera
 * rotation R into {@code drawStack} (= R·P, used for drawing; null when only the basis is updated). The fly-in M is
 * applied in front of P in both: P' = M·P and R·M·P = (R·P)·(P⁻¹·M·P).
 */
@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public abstract class InventoryRendererFlyMixin {
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
        // Asked only when the plane is drawn (drawInventory): for the tests' per-frame count of what is on screen.
        net.shattergui.debug.FrameAudit.plane(cir.getReturnValueF());
    }
}
