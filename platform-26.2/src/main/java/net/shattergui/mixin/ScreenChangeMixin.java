package net.shattergui.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.shattergui.ScreenTransition;
import net.shattergui.render.FrameHooks;
import net.shattergui.ui.preview.RealPreview;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ScreenChangeMixin {
    @Shadow
    private Screen screen;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void shatterGui$blockFromPreview(Screen newScreen, CallbackInfo ci) {
        if (RealPreview.blocksSetScreen(newScreen)) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void shatterGui$before(Screen newScreen, CallbackInfo ci,
                                   @Share("transition") LocalRef<ScreenTransition.State> transition) {
        transition.set(ScreenTransition.before(this.screen, newScreen));
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void shatterGui$after(Screen newScreen, CallbackInfo ci,
                                  @Share("transition") LocalRef<ScreenTransition.State> transition) {
        ScreenTransition.after(this.screen, newScreen, transition.get(), minecraft.level, minecraft.player);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void shatterGui$extractClosingScreen(DeltaTracker deltaTracker, boolean shouldRenderLevel,
                                                 boolean resourcesLoaded, CallbackInfo ci) {
        if (FrameHooks.extractGui(guiRenderState, deltaTracker.getGameTimeDeltaTicks())) {
            ci.cancel();
        }
    }
}
