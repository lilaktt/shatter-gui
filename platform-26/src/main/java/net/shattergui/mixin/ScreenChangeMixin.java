package net.shattergui.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.shattergui.ScreenTransition;
import net.shattergui.ui.preview.RealPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1: the screen lives in {@code Minecraft}. The logic is in {@link ScreenTransition}. */
@Mixin(Minecraft.class)
public abstract class ScreenChangeMixin {
    @Shadow
    public Screen screen;
    @Shadow
    public ClientLevel level;
    @Shadow
    public LocalPlayer player;

    /** A screen made for the editor's real preview must not open anything (it is never shown). */
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
        ScreenTransition.after(this.screen, newScreen, transition.get(), level, player);
    }
}
