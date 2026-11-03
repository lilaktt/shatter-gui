package net.shattergui.shatter;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

/**
 * Moving a body through the world's blocks, axis by axis (x, z, then y), with bounce and ground friction. The one
 * collision code: the built-in shards ({@link ShardInstance}) and the pieces of schema-2 effects both use it.
 */
public final class BlockCollision {
    /** Bits of {@link #move}'s result. */
    public static final int HIT_X = 1;
    public static final int HIT_Z = 2;
    public static final int HIT_CEILING = 4;
    public static final int LANDED = 8;
    /** Below this speed (blocks/tick) a body resting on the ground stops bouncing. */
    public static final double REST_SPEED = 0.02;
    /** Block lookups schema-2 pieces may make per client tick, over all waves (the built-in shards are not counted). */
    public static final int CHECKS_PER_TICK = 4000;
    /** How far from a face a body stops (blocks), so it is clearly outside. */
    private static final double GAP = 1.0e-3;

    private static int checksLeft = CHECKS_PER_TICK;

    private BlockCollision() {
    }

    /** A new client tick: the lookup budget is full again. */
    public static void newTick() {
        checksLeft = CHECKS_PER_TICK;
    }

    /** Takes {@code n} lookups from this tick's budget; false (nothing taken) if there are not that many left. */
    public static boolean reserve(int n) {
        if (checksLeft < n) {
            return false;
        }
        checksLeft -= n;
        return true;
    }

    /**
     * Moves {@code pos} by {@code vel}; an axis that would end inside a block is not moved and its speed bounces back
     * × {@code bounce}. On landing (hitting something while falling) the horizontal speed keeps {@code 1 − friction}
     * and a vertical speed below {@link #REST_SPEED} becomes 0.
     *
     * @param halfSize the body's half extent: the point tested on each axis is that far ahead in the direction of
     *                 motion (0: the centre itself)
     * @return {@link #HIT_X}, {@link #HIT_Z}, {@link #HIT_CEILING}, {@link #LANDED} bits
     */
    public static int move(ClientLevel level, Vector3d pos, Vector3d vel, double halfSize, double bounce, double friction) {
        int hits = 0;
        AABB hit = hitBox(level, pos.x + vel.x + Math.signum(vel.x) * halfSize, pos.y, pos.z);
        if (hit == null) {
            pos.x += vel.x;
        } else {
            double x = vel.x > 0 ? hit.minX - halfSize - GAP : hit.maxX + halfSize + GAP;
            if (between(x, pos.x, pos.x + vel.x) && !collides(level, x, pos.y, pos.z)) {
                pos.x = x;
            }
            vel.x = -vel.x * bounce;
            hits |= HIT_X;
        }

        hit = hitBox(level, pos.x, pos.y, pos.z + vel.z + Math.signum(vel.z) * halfSize);
        if (hit == null) {
            pos.z += vel.z;
        } else {
            double z = vel.z > 0 ? hit.minZ - halfSize - GAP : hit.maxZ + halfSize + GAP;
            if (between(z, pos.z, pos.z + vel.z) && !collides(level, pos.x, pos.y, z)) {
                pos.z = z;
            }
            vel.z = -vel.z * bounce;
            hits |= HIT_Z;
        }

        hit = hitBox(level, pos.x, pos.y + vel.y + Math.signum(vel.y) * halfSize, pos.z);
        if (hit == null) {
            pos.y += vel.y;
        } else {
            // Up to the face it hit, so a fast body does not stop a step short of the ground.
            double y = vel.y > 0 ? hit.minY - halfSize - GAP : hit.maxY + halfSize + GAP;
            if (between(y, pos.y, pos.y + vel.y) && !collides(level, pos.x, y, pos.z)) {
                pos.y = y;
            }
            boolean landed = vel.y < 0;
            vel.y = -vel.y * bounce;
            if (landed) {
                // Ground friction.
                double keep = 1 - friction;
                vel.x *= keep;
                vel.z *= keep;
                if (Math.abs(vel.y) < REST_SPEED) {
                    vel.y = 0;
                }
                hits |= LANDED;
            } else {
                hits |= HIT_CEILING;
            }
        }
        return hits;
    }

    private static boolean between(double v, double a, double b) {
        return v >= Math.min(a, b) && v <= Math.max(a, b);
    }

    /** Whether the point is inside a block's collision shape. */
    public static boolean collides(ClientLevel level, double x, double y, double z) {
        return hitBox(level, x, y, z) != null;
    }

    /** The box of a block's collision shape the point is inside (world coordinates), or null. */
    static AABB hitBox(ClientLevel level, double x, double y, double z) {
        BlockPos blockPos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(blockPos);
        if (state.isAir()) {
            return null;
        }
        VoxelShape shape = state.getCollisionShape(level, blockPos);
        if (shape.isEmpty()) {
            return null;
        }
        double lx = x - blockPos.getX();
        double ly = y - blockPos.getY();
        double lz = z - blockPos.getZ();
        for (AABB box : shape.toAabbs()) {
            if (box.contains(lx, ly, lz)) {
                return box.move(blockPos);
            }
        }
        return null;
    }
}
