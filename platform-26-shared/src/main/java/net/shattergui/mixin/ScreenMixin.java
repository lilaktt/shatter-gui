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

@Mixin(Screen.class)
public abstract class ScreenMixin {
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

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void shatterGui$hideWhileGathering(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                                CallbackInfo ci) {
        if (!ScreenCapture.isExtracting() && OpenAssemble.hidesFlat((Screen) (Object) this)) {
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
