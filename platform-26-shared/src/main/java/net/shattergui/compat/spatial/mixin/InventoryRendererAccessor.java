package net.shattergui.compat.spatial.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Close-animation state of Spatial GUI's {@code InventoryRenderer} (same private fields in 1.8 and 1.9). Targeted by
 * name so nothing here links against Spatial GUI; applied only through {@code SpatialGuiMixinPlugin}.
 */
@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public interface InventoryRendererAccessor {
    /** Animation time left at the start of closing; the plane is drawn only while time is left. */
    @Accessor(value = "closeStartElapsed", remap = false)
    void shatterGui$setCloseStartElapsed(long value);

    /** Total closing time, which also drives the third-person camera return. */
    @Accessor(value = "closeDurationMs", remap = false)
    void shatterGui$setCloseDurationMs(long value);
}
