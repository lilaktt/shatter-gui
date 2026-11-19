package net.shattergui.compat.spatial.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"org.tastytrash.spatialGUI.render.WorldScreenRenderer", "org.tastytrash.spatialGUI.render.InventoryRenderer"},
        remap = false)
public interface SpatialPlaneAccessor {
    @Accessor(value = "closeStartElapsed", remap = false)
    void shatterGui$setCloseStartElapsed(long value);

    @Accessor(value = "closeStartElapsed", remap = false)
    long shatterGui$getCloseStartElapsed();

    @Accessor(value = "closeDurationMs", remap = false)
    void shatterGui$setCloseDurationMs(long value);
}
