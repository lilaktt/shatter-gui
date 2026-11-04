package net.shattergui.shatter;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.shattergui.capture.ScreenCapture.CameraSnapshot;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Where a screen's picture is: the centre of its plane, its axes (right, up, and forward = away from the camera, all
 * unit length) and half its size. The centre is either a world position or relative to the camera (in world axes),
 * depending on who asks.
 */
public record PlaneBasis(Vector3d center, Vector3f right, Vector3f up, Vector3f forward, float halfWidth,
                         float halfHeight) {
    /** Distance of the flat GUI's plane from the camera (the same as {@link CameraPlane}). */
    public static final double FLAT_DISTANCE = 1.0;

    /** The flat GUI's plane of a camera snapshot: centre in world coordinates. */
    public static PlaneBasis flat(CameraSnapshot camera) {
        Vector3f f = camera.forward();
        Vector3d center = new Vector3d(camera.x() + f.x() * FLAT_DISTANCE, camera.y() + f.y() * FLAT_DISTANCE,
                camera.z() + f.z() * FLAT_DISTANCE);
        float halfHeight = (float) (FLAT_DISTANCE * Math.tan(Math.toRadians(camera.fovDegrees()) / 2));
        float halfWidth = halfHeight * camera.windowWidth() / Math.max(1, camera.windowHeight());
        return new PlaneBasis(center, new Vector3f(camera.left()).negate(), new Vector3f(camera.up()), new Vector3f(f),
                halfWidth, halfHeight);
    }

    /** The flat GUI's plane in front of the live camera of this frame: centre relative to the camera. */
    public static PlaneBasis flatNow() {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f f = new Vector3f(camera.forwardVector());
        float halfHeight = (float) (FLAT_DISTANCE * Math.tan(Math.toRadians(camera.getFov()) / 2));
        float halfWidth = halfHeight * mc.getMainRenderTarget().width / Math.max(1, mc.getMainRenderTarget().height);
        return new PlaneBasis(new Vector3d(f.x() * FLAT_DISTANCE, f.y() * FLAT_DISTANCE, f.z() * FLAT_DISTANCE),
                new Vector3f(camera.leftVector()).negate(), new Vector3f(camera.upVector()), f, halfWidth, halfHeight);
    }

    /** The point (x, y) of the picture (fractions, y down), with this basis's centre as origin. */
    public Vector3d point(double x, double y) {
        double r = (x - 0.5) * 2 * halfWidth;
        double u = (0.5 - y) * 2 * halfHeight;
        return new Vector3d(center).add(right.x() * r, right.y() * r, right.z() * r).add(up.x() * u, up.y() * u, up.z() * u);
    }
}
