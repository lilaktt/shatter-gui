package net.shattergui.compat.spatial;

import net.minecraft.world.phys.Vec3;
import net.shattergui.shatter.ShardPlane;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Spatial GUI's screen quad, frozen in world space. Built from its {@code QuadBasis} (camera-relative, world axes) plus
 * the camera position of the frame it was drawn in. The quad maps u along {@code right} and v along {@code up}, with
 * v = 0 at the bottom (VulkanMod flips v), like its render target.
 */
final class SpatialPlane implements ShardPlane {
    private final Vector3d center;
    private final Vector3f right;
    private final Vector3f up;
    private final Vector3f normal;
    private final double halfWidth;
    private final double halfHeight;
    private final boolean flipV;

    SpatialPlane(Vec3 camera, Vector3f centerOffset, Vector3f right, Vector3f up, Vector3f normal, float halfWidth,
                 float halfHeight, boolean flipV) {
        this.center = new Vector3d(camera.x + centerOffset.x(), camera.y + centerOffset.y(), camera.z + centerOffset.z());
        this.right = new Vector3f(right);
        this.up = new Vector3f(up);
        // Face the camera, for lighting.
        this.normal = new Vector3f(normal);
        if (this.normal.dot(centerOffset) > 0) {
            this.normal.negate();
        }
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.flipV = flipV;
    }

    @Override
    public Vector3d toWorld(double x, double y) {
        double r = (x - 0.5) * 2 * halfWidth;
        double u = (0.5 - y) * 2 * halfHeight;
        return new Vector3d(center)
                .add(right.x() * r, right.y() * r, right.z() * r)
                .add(up.x() * u, up.y() * u, up.z() * u);
    }

    @Override
    public double aspect() {
        return halfWidth / halfHeight;
    }

    @Override
    public Vector3f normal() {
        return normal;
    }

    @Override
    public float v(double y) {
        return flipV ? (float) y : (float) (1 - y);
    }

    Vector3d center() {
        return center;
    }
}
