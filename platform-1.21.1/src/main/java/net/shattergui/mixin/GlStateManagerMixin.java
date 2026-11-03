package net.shattergui.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.shattergui.capture.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a closing screen is drawn into its capture slot, every blend accumulates alpha ({@code ONE,
 * ONE_MINUS_SRC_ALPHA}) instead of whatever the GUI asked for (vanilla's default writes the source alpha over the
 * destination, which would punch holes into the transparent picture where partly transparent sprites lie on the
 * panel). Colours blend as asked. Changed before GlStateManager's own state cache, so the cache stays true to GL.
 */
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
