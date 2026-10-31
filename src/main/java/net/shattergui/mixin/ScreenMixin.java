package net.shattergui.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <ul>
 *   <li>While a closing screen is captured, skip the shared in-world background (blur, darkening, menu texture) so only
 *       the screen's own panel ends up in the image. Container panels are drawn by subclass overrides and are
 *       unaffected.</li>
 *   <li>While a flat screen flies in ({@link FlyOpen}), its GUI is moved and scaled from the container, the shared
 *       background is drawn untransformed, and the mouse is kept off the GUI (nothing hovered, no tooltip).</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    /** Far outside the screen so nothing is hovered and no tooltip is drawn. */
    @Unique
    private static final int NO_MOUSE = -100_000;

    @Unique
    private boolean shatterGui$flyPushed;
    @Unique
    private int shatterGui$backgroundPushed;

    /**
     * While the screen is being gathered by an open effect, the pieces stand in for it: it is not drawn. Not on a Spatial
     * GUI plane: Spatial GUI draws its texture from this very call, and hides the plane instead.
     */
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void shatterGui$hideWhileGathering(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                                CallbackInfo ci) {
        if (!ScreenCapture.isExtracting() && OpenAssemble.hidesFlat((Screen) (Object) this)) {
            ci.cancel();
        } else if (!ScreenCapture.isExtracting() && (Object) this == net.minecraft.client.Minecraft.getInstance().screen) {
            net.shattergui.debug.FrameAudit.screenExtracted();
        }
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void shatterGui$flyInStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                       CallbackInfo ci) {
        shatterGui$flyPushed = !ScreenCapture.isExtracting() && FlyOpen.pushFlatTransform((Screen) (Object) this, graphics);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void shatterGui$flyInEnd(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        if (shatterGui$flyPushed) {
            shatterGui$flyPushed = false;
            FlyOpen.popFlatTransform(graphics);
        }
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int shatterGui$hideMouseX(int mouseX) {
        return FlyOpen.isAnimating((Screen) (Object) this) ? NO_MOUSE : mouseX;
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int shatterGui$hideMouseY(int mouseY) {
        return FlyOpen.isAnimating((Screen) (Object) this) ? NO_MOUSE : mouseY;
    }

    @Inject(method = {
            "extractBlurredBackground",
            "extractTransparentBackground",
            "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
    }, at = @At("HEAD"), cancellable = true)
    private void shatterGui$background(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (ScreenCapture.isExtracting()) {
            ci.cancel();
            return;
        }
        if (shatterGui$flyPushed) {
            // The darkened/blurred world behind the GUI does not fly.
            graphics.pose().pushMatrix().identity();
            shatterGui$backgroundPushed++;
        }
    }

    @Inject(method = {
            "extractBlurredBackground",
            "extractTransparentBackground",
            "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
    }, at = @At("RETURN"))
    private void shatterGui$backgroundEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (shatterGui$backgroundPushed > 0) {
            shatterGui$backgroundPushed--;
            graphics.pose().popMatrix();
        }
    }
}
