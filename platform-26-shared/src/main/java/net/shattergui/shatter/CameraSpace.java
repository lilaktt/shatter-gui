package net.shattergui.shatter;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class CameraSpace {
    private final double ox;
    private final double oy;
    private final double oz;
    private final Matrix3f linear;
    private final Vector3f offset;
    private final float world;

    private CameraSpace(double ox, double oy, double oz, Matrix3f linear, Vector3f offset, float world, Vec3 camera) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.linear = linear;
        this.offset = offset;
        this.world = world;
        this.camera = camera;
    }

    private final Vec3 camera;

    public static CameraSpace world(Vec3 camera) {
        return new CameraSpace(camera.x, camera.y, camera.z, null, new Vector3f(), 0f, camera);
    }

    public static CameraSpace follow(PlaneBasis captured, PlaneBasis now) {
        float sx = now.halfWidth() / Math.max(1e-6f, captured.halfWidth());
        float sy = now.halfHeight() / Math.max(1e-6f, captured.halfHeight());
        Matrix3f to = new Matrix3f(new Vector3f(now.right()).mul(sx), new Vector3f(now.up()).mul(sy),
                new Vector3f(now.forward()).mul(sy));
        Matrix3f from = new Matrix3f(captured.right(), captured.up(), captured.forward()).transpose();
        Matrix3f linear = to.mul(from);
        Vector3d c = captured.center();
        return new CameraSpace(c.x, c.y, c.z, linear, new Vector3f((float) now.center().x, (float) now.center().y,
                (float) now.center().z), 0f, null);
    }

    public CameraSpace towardsWorld(Vec3 camera, float amount) {
        return new CameraSpace(ox, oy, oz, linear, offset, Math.clamp(amount, 0f, 1f), camera);
    }

    public org.joml.Quaternionf rotation(org.joml.Quaternionf out) {
        out.identity();
        if (linear != null) {
            linear.getNormalizedRotation(out);
            out.slerp(new org.joml.Quaternionf(), world);
        }
        return out;
    }

    public boolean follows() {
        return linear != null;
    }

    public Vector3f point(double x, double y, double z, Vector3f out) {
        out.set((float) (x - ox), (float) (y - oy), (float) (z - oz));
        if (linear != null) {
            linear.transform(out);
        }
        out.add(offset);
        if (world > 0f && camera != null) {
            out.lerp(new Vector3f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)), world);
        }
        return out;
    }

    public Vector3f offset(Vector3f v) {
        if (linear == null) {
            return v;
        }
        Vector3f original = world > 0f ? new Vector3f(v) : null;
        linear.transform(v);
        return original != null ? v.lerp(original, world) : v;
    }

    public Vector3f direction(Vector3f v) {
        return linear != null ? offset(v).normalize() : v;
    }
}
