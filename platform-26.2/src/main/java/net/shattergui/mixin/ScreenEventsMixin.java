package net.shattergui.mixin;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.impl.client.screen.ScreenEventFactory;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ui.preview.RealPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ScreenEvents.class, remap = false)
public abstract class ScreenEventsMixin {
    @Unique
    private static final Event<ScreenEvents.AfterForeground> SHATTER_GUI$NOBODY = ScreenEventFactory.createAfterForegroundEvent();

    @Inject(method = "afterForeground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void shatterGui$quietPreview(Screen screen, CallbackInfoReturnable<Event<ScreenEvents.AfterForeground>> cir) {
        if (RealPreview.isExtracting()) {
            cir.setReturnValue(SHATTER_GUI$NOBODY);
        }
    }
}
