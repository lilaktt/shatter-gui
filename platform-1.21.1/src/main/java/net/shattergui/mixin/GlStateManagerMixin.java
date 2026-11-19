package net.shattergui.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.shattergui.capture.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    private static final int GL_ONE = 1;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;

    @Inject(method = "_blendFunc", at = @At("HEAD"), cancellable = true, remap = false)
    private static void shatterGui$blendFunc(int src, int dst, CallbackInfo ci) {
        if (ScreenCapture.isExtracting()) {
            ci.cancel();
            GlStateManager._blendFuncSeparate(src, dst, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @ModifyVariable(method = "_blendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false)
    private static int shatterGui$srcAlpha(int srcAlpha) {
        return ScreenCapture.isExtracting() ? GL_ONE : srcAlpha;
    }

    @ModifyVariable(method = "_blendFuncSeparate", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false)
    private static int shatterGui$dstAlpha(int dstAlpha) {
        return ScreenCapture.isExtracting() ? GL_ONE_MINUS_SRC_ALPHA : dstAlpha;
    }
}
