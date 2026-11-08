package net.shattergui.shatter;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.shattergui.capture.ScreenCapture.CameraSnapshot;
import net.shattergui.platform.impl.Mc26;
import org.joml.Vector3d;
import org.joml.Vector3f;

public record PlaneBasis(Vector3d center, Vector3f right, Vector3f up, Vector3f forward, float halfWidth,
                         float halfHeight) {
    public static final double FLAT_DISTANCE = 1.0;

    public static PlaneBasis flat(CameraSnapshot camera) {
        Vector3f f = camera.forward();
        Vector3d center = new Vector3d(camera.x() + f.x() * FLAT_DISTANCE, camera.y() + f.y() * FLAT_DISTANCE,
                camera.z() + f.z() * FLAT_DISTANCE);
        float halfHeight = (float) (FLAT_DISTANCE * Math.tan(Math.toRadians(camera.fovDegrees()) / 2));
        float halfWidth = halfHeight * camera.windowWidth() / Math.max(1, camera.windowHeight());
        return new PlaneBasis(center, new Vector3f(camera.left()).negate(), new Vector3f(camera.up()), new Vector3f(f),
                halfWidth, halfHeight);
    }

    public static PlaneBasis flatNow() {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = Mc26.camera();
        Vector3f f = new Vector3f(camera.forwardVector());
        float halfHeight = (float) (FLAT_DISTANCE * Math.tan(Math.toRadians(camera.getFov()) / 2));
        float halfWidth = halfHeight * Mc26.mainTarget().width / Math.max(1, Mc26.mainTarget().height);
        return new PlaneBasis(new Vector3d(f.x() * FLAT_DISTANCE, f.y() * FLAT_DISTANCE, f.z() * FLAT_DISTANCE),
                new Vector3f(camera.leftVector()).negate(), new Vector3f(camera.upVector()), f, halfWidth, halfHeight);
    }

    public Vector3d point(double x, double y) {
        double r = (x - 0.5) * 2 * halfWidth;
        double u = (0.5 - y) * 2 * halfHeight;
        return new Vector3d(center).add(right.x() * r, right.y() * r, right.z() * r).add(up.x() * u, up.y() * u, up.z() * u);
    }
}
