package net.shattergui.shatter;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * How a wave's world positions become the camera-relative positions it is drawn at (world axes, the camera at the
 * origin) in one frame.
 *
 * <ul>
 *   <li>{@link #world}: where it is in the world, seen from the camera ({@code position − camera}).</li>
 *   <li>{@link #follow}: an opening that follows the camera. Everything is placed relative to the plane it was cut from
 *       ({@code captured}) and put down the same way relative to where that plane is in this frame ({@code now}, relative
 *       to the live camera): in front of the face while the player walks or turns, and exactly where the real screen
 *       appears when it lands. Sizes along the plane follow its size (the field of view widens while sprinting); depth
 *       follows its height.</li>
 * </ul>
 */
public final class CameraSpace {
    private final double ox;
    private final double oy;
    private final double oz;
    /** World offset → camera-relative offset; null for none (a plain translation). */
    private final Matrix3f linear;
    private final Vector3f offset;
    /** Blended with the plain world space by this much (0: only this one), see {@link #towardsWorld}. */
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

    /** The live camera, for {@link #towardsWorld}. */
    private final Vec3 camera;

    public static CameraSpace world(Vec3 camera) {
        return new CameraSpace(camera.x, camera.y, camera.z, null, new Vector3f(), 0f, camera);
    }

    /** {@code captured}: centre in world coordinates; {@code now}: centre relative to the live camera. */
    public static CameraSpace follow(PlaneBasis captured, PlaneBasis now) {
        float sx = now.halfWidth() / Math.max(1e-6f, captured.halfWidth());
        float sy = now.halfHeight() / Math.max(1e-6f, captured.halfHeight());
        // Columns: where the captured right, up and forward go now (scaled), times world → captured axes.
        Matrix3f to = new Matrix3f(new Vector3f(now.right()).mul(sx), new Vector3f(now.up()).mul(sy),
                new Vector3f(now.forward()).mul(sy));
        Matrix3f from = new Matrix3f(captured.right(), captured.up(), captured.forward()).transpose();
        Matrix3f linear = to.mul(from);
        Vector3d c = captured.center();
        return new CameraSpace(c.x, c.y, c.z, linear, new Vector3f((float) now.center().x, (float) now.center().y,
                (float) now.center().z), 0f, null);
    }

    /**
     * A close that leaves from where the screen is now (the fly effect following the camera): this space blended into
     * the world space of {@code camera} by {@code amount} — 0 at the start (in front of the face), 1 at the end (its
     * target in the world).
     */
    public CameraSpace towardsWorld(Vec3 camera, float amount) {
        return new CameraSpace(ox, oy, oz, linear, offset, Math.clamp(amount, 0f, 1f), camera);
    }

    public boolean follows() {
        return linear != null;
    }

    /** A world position → where it is drawn, relative to the camera. */
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

    /** A world offset (a shard's corner) → the offset it is drawn with. */
    public Vector3f offset(Vector3f v) {
        if (linear == null) {
            return v;
        }
        Vector3f original = world > 0f ? new Vector3f(v) : null;
        linear.transform(v);
        return original != null ? v.lerp(original, world) : v;
    }

    /** A unit direction (a normal) → the direction it is drawn with, unit length. */
    public Vector3f direction(Vector3f v) {
        return linear != null ? offset(v).normalize() : v;
    }
}
