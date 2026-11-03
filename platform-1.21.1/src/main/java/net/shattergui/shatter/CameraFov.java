package net.shattergui.shatter;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.shattergui.mixin.GameRendererAccessor;

/**
 * The vertical field of view the level is drawn with, in degrees (26.1 has {@code Camera#getFov}; 1.21.1 computes it
 * in the private {@code GameRenderer#getFov(Camera, float, boolean)}, with the FOV setting, its effects, water and
 * death), so the plane of the shards covers exactly what the player sees.
 */
public final class CameraFov {
    private CameraFov() {
    }

    public static float of(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return (float) ((GameRendererAccessor) mc.gameRenderer).shatterGui$getFov(camera, camera.getPartialTickTime(), true);
    }
}
