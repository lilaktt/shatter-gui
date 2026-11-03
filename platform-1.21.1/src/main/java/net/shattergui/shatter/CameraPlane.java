package net.shattergui.shatter;

import net.shattergui.capture.ScreenCapture.CameraSnapshot;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** The flat GUI: a camera-facing plane that covers exactly the field of view, so shards start where the GUI was. */
public final class CameraPlane implements ShardPlane {
    /** Distance from the camera to the plane, in blocks. */
    private static final double DISTANCE = 1.0;

    private final CameraSnapshot camera;
    private final Vector3d center;
    private final double height;
    private final double width;
    private final Vector3f normal;

    public CameraPlane(CameraSnapshot camera) {
        this.camera = camera;
        Vector3f f = camera.forward();
        this.center = new Vector3d(camera.x() + f.x() * DISTANCE, camera.y() + f.y() * DISTANCE,
                camera.z() + f.z() * DISTANCE);
        this.height = 2 * DISTANCE * Math.tan(Math.toRadians(camera.fovDegrees()) / 2);
        this.width = height * camera.windowWidth() / camera.windowHeight();
        this.normal = new Vector3f(f).negate();
    }

    @Override
    public Vector3d toWorld(double x, double y) {
        double right = (x - 0.5) * width;
        double down = (y - 0.5) * height;
        Vector3f left = camera.left();
        Vector3f up = camera.up();
        return new Vector3d(center)
                .sub(left.x() * right, left.y() * right, left.z() * right)
                .sub(up.x() * down, up.y() * down, up.z() * down);
    }

    @Override
    public double aspect() {
        return width / height;
    }

    @Override
    public Vector3f normal() {
        return normal;
    }
}
