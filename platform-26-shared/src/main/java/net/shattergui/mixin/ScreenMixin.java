package net.shattergui.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenBackdrop;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.platform.impl.Mc26;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <ul>
 *   <li>While a closing screen is captured, skip the shared in-world background (blur, darkening, menu texture) so only
 *       the screen's own panel ends up in the image. Container panels are drawn by subclass overrides and are
 *       unaffected.</li>
 *   <li>While a flat screen flies in ({@link FlyOpen}), its GUI is moved and scaled from the container, the shared
 *       background is drawn untransformed, and the mouse is kept off the GUI (nothing hovered, no tooltip).</li>
 *   <li>While a screen opens with an effect, its background fades in with it ({@link OpenBackdrop}); while it is
 *       gathered only the background is drawn.</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    /** Far outside the screen so nothing is hovered and no tooltip is drawn. */
    @Unique
    private static final int NO_MOUSE = -100_000;

    @Shadow
    protected abstract void extractBlurredBackground(GuiGraphicsExtractor graphics);

    @Shadow
    protected abstract void extractMenuBackground(GuiGraphicsExtractor graphics);

    @Unique
    private void shatterGui$extractBlurredBackground(GuiGraphicsExtractor graphics) {
        extractBlurredBackground(graphics);
    }

    @Unique
    private void shatterGui$extractMenuBackground(GuiGraphicsExtractor graphics) {
        extractMenuBackground(graphics);
    }

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
            // Only the darkened/blurred world behind it, fading in with the gathering (OpenBackdrop); the pieces are
            // drawn after the GUI, over it, where the screen will be. Not extractBackground: container screens
            // override it to draw their panel too, which the pieces stand in for.
            graphics.nextStratum();
            if (((Screen) (Object) this).isInGameUi()) {
                ((Screen) (Object) this).extractTransparentBackground(graphics);
            } else {
                shatterGui$extractBlurredBackground(graphics);
                shatterGui$extractMenuBackground(graphics);
            }
            ci.cancel();
        } else if (!ScreenCapture.isExtracting() && (Object) this == net.shattergui.platform.impl.Mc26.screen()) {
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

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"))
    private void shatterGui$gradientKind(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ScreenCapture.isExtracting()) {
            OpenBackdrop.beginDrawing((Screen) (Object) this, OpenBackdrop.GRADIENT);
        }
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"))
    private void shatterGui$blurKind(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ScreenCapture.isExtracting()
                && net.minecraft.client.Minecraft.getInstance().options.getMenuBackgroundBlurriness() >= 1f) {
            OpenBackdrop.beginDrawing((Screen) (Object) this, OpenBackdrop.BLUR);
        }
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"))
    private void shatterGui$menuKind(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ScreenCapture.isExtracting()) {
            OpenBackdrop.beginDrawing((Screen) (Object) this, OpenBackdrop.MENU);
        }
    }

    /** The darkening fades with the opening: its alpha × {@link OpenBackdrop#factor} (1 = vanilla). */
    @ModifyArg(method = "extractTransparentBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"), index = 4)
    private int shatterGui$fadeGradientTop(int color) {
        return OpenBackdrop.fade(color);
    }

    @ModifyArg(method = "extractTransparentBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"), index = 5)
    private int shatterGui$fadeGradientBottom(int color) {
        return OpenBackdrop.fade(color);
    }

    /** The in-world menu texture fades the same way (drawn white-tinted with the faded alpha). */
    @Redirect(method = "extractMenuBackgroundTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private static void shatterGui$fadeMenuTexture(GuiGraphicsExtractor graphics,
                                                   com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                                   net.minecraft.resources.Identifier texture, int x, int y, float u,
                                                   float v, int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, OpenBackdrop.menuTint());
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
        OpenBackdrop.endDrawing();
        if (shatterGui$backgroundPushed > 0) {
            shatterGui$backgroundPushed--;
            graphics.pose().popMatrix();
        }
    }
}
