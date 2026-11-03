package net.shattergui.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Panel bounds of a container screen, in GUI-scaled pixels. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int shatterGui$leftPos();

    @Accessor("topPos")
    int shatterGui$topPos();

    @Accessor("imageWidth")
    int shatterGui$imageWidth();

    @Accessor("imageHeight")
    int shatterGui$imageHeight();
}
