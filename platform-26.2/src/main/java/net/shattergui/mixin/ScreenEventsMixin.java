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

/**
 * Since 26.2 Fabric fires this event inside {@code AbstractContainerScreen#extractRenderState}, and a screen without
 * Fabric's per-screen events throws there. The real preview's screens have none on purpose (only the protected
 * {@code init()} is called, so nobody hears of them): for them the event is an empty one. {@code require = 0}: if Fabric
 * changes this, the preview falls back to the simple samples, as for any other failure.
 */
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
