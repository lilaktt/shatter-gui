package net.shattergui.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.shattergui.fly.FlyOpen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves the player model out of a flying flat GUI; see {@link FlyOpen#hidesPictureInPicture}. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), cancellable = true)
    private static void shatterGui$hideWhileFlying(GuiGraphics graphics, int x0, int y0, int x1, int y1, int scale,
                                                   float yOffset, float mouseX, float mouseY, LivingEntity entity,
                                                   CallbackInfo ci) {
        if (FlyOpen.hidesPictureInPicture()) {
            ci.cancel();
        }
    }
}
