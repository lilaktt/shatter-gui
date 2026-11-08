package net.shattergui.compat.spatial.mixin;

import net.shattergui.fly.OpenAssemble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.tastytrash.spatialGUI.render.WorldBlurRenderer", remap = false)
public abstract class WorldBlurMixin {
    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, remap = false)
    private static void shatterGui$noBlurWhileGathering(int strength, CallbackInfo ci) {
        if (OpenAssemble.spatialAlpha() < 1f) {
            ci.cancel();
        }
    }
}
