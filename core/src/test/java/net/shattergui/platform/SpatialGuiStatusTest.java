package net.shattergui.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialGuiStatusTest {
    @Test
    void linesSayVersionStateAndReason() {
        assertEquals("Spatial GUI: not installed, bridge disabled, nothing to bridge",
                SpatialGuiBridge.Status.notInstalled().line());
        assertEquals("Spatial GUI: 1.11.0, bridge enabled, plane WorldScreenRenderer",
                new SpatialGuiBridge.Status("1.11.0", true, SpatialGuiBridge.Reason.READY, "plane WorldScreenRenderer").line());
        assertEquals("Spatial GUI: 1.12.0, bridge disabled, this version lacks: WorldScreenRenderer.closeStartElapsed",
                new SpatialGuiBridge.Status("1.12.0", false, SpatialGuiBridge.Reason.MISSING,
                        " WorldScreenRenderer.closeStartElapsed ").line());
        assertEquals("Spatial GUI: 1.10.1, bridge disabled, turned off in settings (spatialGuiIntegration)",
                new SpatialGuiBridge.Status("1.10.1", false, SpatialGuiBridge.Reason.SETTING_OFF, null).line());
        assertEquals("Spatial GUI: 1.10.1, bridge disabled, error: NoSuchMethodError x",
                new SpatialGuiBridge.Status("1.10.1", false, SpatialGuiBridge.Reason.FAILED, "NoSuchMethodError x").line());
    }

    @Test
    void everyReasonHasAnAboutKey() {
        for (SpatialGuiBridge.Reason reason : SpatialGuiBridge.Reason.values()) {
            assertTrue(reason.key().startsWith("ui.shatter_gui.about.spatial."), reason.key());
        }
    }

    @Test
    void platformsWithoutABridgeSaySo() {
        SpatialGuiBridge none = () -> {
        };
        assertEquals("Spatial GUI: not checked, bridge disabled, no Spatial GUI bridge on this platform", none.status().line());
    }
}
