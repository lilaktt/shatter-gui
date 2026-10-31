package net.shattergui.compat.spatial.mixin;

import net.shattergui.fly.OpenAssemble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spatial GUI's optional world blur ({@code blurWorldBackground}, off by default) is drawn after the level, so it would
 * blur the pieces of a screen that gathers on its plane: no blur until the plane appears. Same static method in 1.8 and
 * 1.9; applied only through {@code SpatialGuiMixinPlugin}.
 */
@Mixin(targets = "org.tastytrash.spatialGUI.render.WorldBlurRenderer", remap = false)
public abstract class WorldBlurMixin {
    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, remap = false)
    private static void shatterGui$noBlurWhileGathering(int strength, CallbackInfo ci) {
        if (OpenAssemble.spatialAlpha() < 1f) {
            ci.cancel();
        }
    }
}
