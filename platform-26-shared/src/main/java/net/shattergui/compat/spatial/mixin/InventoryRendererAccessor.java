package net.shattergui.compat.spatial.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public interface InventoryRendererAccessor {
    @Accessor(value = "closeStartElapsed", remap = false)
    void shatterGui$setCloseStartElapsed(long value);

    @Accessor(value = "closeDurationMs", remap = false)
    void shatterGui$setCloseDurationMs(long value);
}
