package net.shattergui.shatter;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.shattergui.mixin.GameRendererAccessor;

public final class CameraFov {
    private CameraFov() {
    }

    public static float of(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return (float) ((GameRendererAccessor) mc.gameRenderer).shatterGui$getFov(camera, camera.getPartialTickTime(), true);
    }
}
