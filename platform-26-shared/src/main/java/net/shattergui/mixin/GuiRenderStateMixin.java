package net.shattergui.mixin;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.shattergui.fly.FlyOpen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), cancellable = true)
    private void shatterGui$hideWhileFlying(PictureInPictureRenderState state, CallbackInfo ci) {
        if (FlyOpen.hidesPictureInPicture()) {
            ci.cancel();
        }
    }
}
