package net.shattergui.shatter;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

public final class BlockCollision {
    public static final int HIT_X = 1;
    public static final int HIT_Z = 2;
    public static final int HIT_CEILING = 4;
    public static final int LANDED = 8;
    public static final double REST_SPEED = 0.02;
    public static final int CHECKS_PER_TICK = 4000;
    private static final double GAP = 1.0e-3;

    private static int checksLeft = CHECKS_PER_TICK;

    private BlockCollision() {
    }

    public static void newTick() {
        checksLeft = CHECKS_PER_TICK;
    }

    public static boolean reserve(int n) {
        if (checksLeft < n) {
            return false;
        }
        checksLeft -= n;
        return true;
    }

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
            double y = vel.y > 0 ? hit.minY - halfSize - GAP : hit.maxY + halfSize + GAP;
            if (between(y, pos.y, pos.y + vel.y) && !collides(level, pos.x, y, pos.z)) {
                pos.y = y;
            }
            boolean landed = vel.y < 0;
            vel.y = -vel.y * bounce;
            if (landed) {
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

    public static boolean collides(ClientLevel level, double x, double y, double z) {
        return hitBox(level, x, y, z) != null;
    }

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
