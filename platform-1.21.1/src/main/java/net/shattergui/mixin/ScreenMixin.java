package net.shattergui.mixin;

import net.minecraft.client.gui.GuiGraphics;
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

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final int NO_MOUSE = -100_000;

    @Unique
    private boolean shatterGui$flyPushed;
    @Unique
    private int shatterGui$backgroundPushed;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void shatterGui$hideWhileGathering(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                CallbackInfo ci) {
        if (!ScreenCapture.isExtracting() && OpenAssemble.hidesFlat((Screen) (Object) this)) {
            ci.cancel();
        } else if (!ScreenCapture.isExtracting() && (Object) this == net.minecraft.client.Minecraft.getInstance().screen) {
            net.shattergui.debug.FrameAudit.screenExtracted();
        }
    }

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void shatterGui$flyInStart(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                       CallbackInfo ci) {
        shatterGui$flyPushed = !ScreenCapture.isExtracting() && FlyOpen.pushFlatTransform((Screen) (Object) this, graphics);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void shatterGui$flyInEnd(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        if (shatterGui$flyPushed) {
            shatterGui$flyPushed = false;
            FlyOpen.popFlatTransform(graphics);
        }
    }

    @ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int shatterGui$hideMouseX(int mouseX) {
        return FlyOpen.isAnimating((Screen) (Object) this) ? NO_MOUSE : mouseX;
    }

    @ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int shatterGui$hideMouseY(int mouseY) {
        return FlyOpen.isAnimating((Screen) (Object) this) ? NO_MOUSE : mouseY;
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void shatterGui$blur(float partialTick, CallbackInfo ci) {
        if (ScreenCapture.isExtracting()) {
            ci.cancel();
        }
    }

    @Inject(method = {
            "renderTransparentBackground",
            "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V"
    }, at = @At("HEAD"), cancellable = true)
    private void shatterGui$background(GuiGraphics graphics, CallbackInfo ci) {
        if (ScreenCapture.isExtracting()) {
            ci.cancel();
            return;
        }
        if (shatterGui$flyPushed) {
            graphics.pose().pushPose();
            graphics.pose().setIdentity();
            shatterGui$backgroundPushed++;
        }
    }

    @Inject(method = {
            "renderTransparentBackground",
            "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V"
    }, at = @At("RETURN"))
    private void shatterGui$backgroundEnd(GuiGraphics graphics, CallbackInfo ci) {
        if (shatterGui$backgroundPushed > 0) {
            shatterGui$backgroundPushed--;
            graphics.pose().popPose();
        }
    }
}
