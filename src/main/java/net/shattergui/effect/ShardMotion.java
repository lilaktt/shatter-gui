package net.shattergui.effect;

import net.minecraft.util.RandomSource;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Initial-motion helpers shared by effects. */
final class ShardMotion {
    private ShardMotion() {
    }

    /**
     * Radial push from the image center, plus forward along the view, plus an upward kick and noise.
     * All parts scale with {@code burst} (blocks/tick).
     */
    static void burst(ShardInstance shard, ShardMesh.Layout layout, Vector3f forward, double burst, double upward,
                      RandomSource random) {
        Vector3d radial = new Vector3d(shard.pos()).sub(layout.center());
        double distance = radial.length();
        if (distance > 1.0e-6) {
            radial.div(distance);
        }
        double radialScale = 0.5 + distance / Math.max(layout.halfDiagonal(), 1.0e-6);
        double noise = burst * 0.15;
        shard.vel().set(radial).mul(burst * radialScale * 0.6)
                .add(forward.x() * burst * 0.5, forward.y() * burst * 0.5 + burst * upward, forward.z() * burst * 0.5)
                .add(random.nextGaussian() * noise, random.nextGaussian() * noise, random.nextGaussian() * noise);
    }

    /** Random axis, rate between 30% and 100% of {@code spin} (rad/tick). */
    static void spin(ShardInstance shard, float spin, RandomSource random) {
        Vector3f axis = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
        if (axis.lengthSquared() < 1.0e-4f) {
            axis.set(0, 1, 0);
        }
        axis.normalize().mul(spin * (0.3f + random.nextFloat() * 0.7f));
        shard.angVel().set(axis);
    }

    static int white(float alpha) {
        return (Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24) | 0xFFFFFF;
    }
}
