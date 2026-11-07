package net.shattergui.shatter;

import net.shattergui.platform.impl.Mc26;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** One triangular shard: world-space rigid body rotating around its centroid. Effects drive it via {@link #tick}. */
public final class ShardInstance {
    public static final int FULL_BRIGHT = 0xF000F0;
    /** Share of the horizontal speed a landing shard loses. */
    private static final double GROUND_FRICTION = 0.4;
    /** Share of its tilt a shard on the ground loses each tick as it falls over onto its face. */
    private static final float TOPPLE = 0.35f;
    /** How far above a face a resting shard stays (as {@code BlockCollision}). */
    private static final double GAP = 1.0e-3;
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f DOWN = new Vector3f(0f, -1f, 0f);
    private final Vector3f scratch = new Vector3f();
    private final Vector3f axis = new Vector3f();

    /** Triangle corners relative to the centroid, in world axes at spawn orientation. */
    final Vector3f[] corners;
    /** u0, v0, u1, v1, u2, v2 */
    final float[] uv;
    final Vector3f normal;

    final Vector3d pos = new Vector3d();
    final Vector3d prevPos = new Vector3d();
    final Vector3d vel = new Vector3d();
    final Quaternionf rot = new Quaternionf();
    final Quaternionf prevRot = new Quaternionf();
    final Vector3f angVel = new Vector3f();
    private final Quaternionf rotStep = new Quaternionf();

    int age;
    int lifetime;
    /** Free per-shard value for the effect (e.g. the tick a burning cell catches fire). */
    public float effectData;
    /** Free per-shard object for the effect (schema-2 effects: which piece of which wave). */
    public Object effectState;
    int fadeStart;
    int light = FULL_BRIGHT;
    /** Random value in [0, 1) fixed per shard, for effects that need per-shard variation. */
    final float seed;
    /** Spawned inside a block: ignore collisions until it is out. */
    boolean noClip;
    /** Ticks motionless in a row ({@link Settle}). */
    private int stillTicks;
    /** Settled: drawn in the world's depth with "alwaysOnTop" ({@link Settle}). */
    private boolean settled;
    /** The farthest corner from the centroid (blocks), or -1 before it is needed. */
    private float extent = -1f;

    ShardInstance(Vector3d centroid, Vector3f[] corners, float[] uv, Vector3f normal, int lifetime, float fadeStartFraction,
                  float seed) {
        this.pos.set(centroid);
        this.prevPos.set(centroid);
        this.corners = corners;
        this.uv = uv;
        this.normal = normal;
        this.lifetime = lifetime;
        this.fadeStart = Math.round(lifetime * fadeStartFraction);
        this.seed = seed;
    }

    public Vector3d pos() {
        return pos;
    }

    public Vector3d prevPos() {
        return prevPos;
    }

    public Vector3d vel() {
        return vel;
    }

    public Quaternionf rot() {
        return rot;
    }

    public Quaternionf prevRot() {
        return prevRot;
    }

    public Vector3f angVel() {
        return angVel;
    }

    public Vector3f[] corners() {
        return corners;
    }

    public float[] uv() {
        return uv;
    }

    public Vector3f normal() {
        return normal;
    }

    public int light() {
        return light;
    }

    public int age() {
        return age;
    }

    public int lifetime() {
        return lifetime;
    }

    public float seed() {
        return seed;
    }

    /** For effects that decide themselves how long a shard lives. */
    public void setLifetime(int lifetime) {
        this.lifetime = Math.max(1, lifetime);
        this.fadeStart = this.lifetime;
    }

    /** Settled (see {@link Settle}): lies still on a block or is old, so it is drawn in the world's depth. */
    public boolean settled() {
        return settled;
    }

    /**
     * After this tick's motion: whether the shard is settled now. {@code eligible} false (openings, a fly that follows the
     * camera, previews): never.
     */
    void updateSettled(ClientLevel level, boolean eligible) {
        if (!eligible) {
            stillTicks = 0;
            settled = false;
            return;
        }
        boolean moved = Settle.moved(pos.x - prevPos.x, pos.y - prevPos.y, pos.z - prevPos.z, Settle.halfTurnSin(
                prevRot.x, prevRot.y, prevRot.z, prevRot.w, rot.x, rot.y, rot.z, rot.w));
        stillTicks = Settle.stillTicks(stillTicks, moved);
        if (moved && age < Settle.AGE_TICKS) {
            settled = false;
            return;
        }
        if (!Settle.recheck(stillTicks, moved, age)) {
            return;
        }
        boolean inside = collides(level, pos.x, pos.y, pos.z);
        boolean supported = !inside && collides(level, pos.x, pos.y - extent() - Settle.SUPPORT_REACH, pos.z);
        settled = Settle.settled(stillTicks, supported, age, inside);
    }

    /** The farthest corner from the centroid (blocks): how far below the centroid the shard can reach. */
    private float extent() {
        if (extent < 0f) {
            float max = 0f;
            for (Vector3f corner : corners) {
                max = Math.max(max, corner.length());
            }
            extent = max;
        }
        return extent;
    }

    public boolean isDead() {
        return age >= lifetime;
    }

    /** Starts the shard's life over (an effect recorded ahead of time and now played back). */
    public void restart(int lifetime) {
        age = 0;
        setLifetime(lifetime);
    }

    public void setLight(int light) {
        this.light = light;
    }

    /** Pushes the shard straight into its fade phase (used when the shard limit is exceeded). */
    void hurryFade() {
        if (age < fadeStart) {
            fadeStart = age;
        }
    }

    /** 1 until the fade starts, then down to 0 at the end of life. */
    public float alpha(float partialTick) {
        float t = age + partialTick;
        if (t <= fadeStart) {
            return 1f;
        }
        float span = Math.max(1, lifetime - fadeStart);
        return Math.clamp(1f - (t - fadeStart) / span, 0f, 1f);
    }

    /** Fraction of the lifetime that has passed, 0..1. */
    public float progress(float partialTick) {
        return Math.clamp((age + partialTick) / lifetime, 0f, 1f);
    }

    /** One step without any motion: the shard stays where it was cut, only its age advances. */
    public void tickStatic() {
        prevPos.set(pos);
        prevRot.set(rot);
        age++;
    }

    /** One physics step: gravity, drag, block collisions (unless {@code collide} is false: low quality), spin. */
    public void tick(ClientLevel level, float gravity, float airDrag, float bounce, boolean fullbright, boolean collide) {
        prevPos.set(pos);
        prevRot.set(rot);
        age++;

        vel.y -= gravity;
        vel.mul(airDrag);
        angVel.mul(airDrag);

        boolean landed = false;
        if (!collide) {
            pos.add(vel);
        } else if (noClip) {
            pos.add(vel);
            noClip = collides(level, pos.x, pos.y, pos.z);
        } else {
            landed = moveWithCollisions(level, bounce);
        }

        float rate = angVel.length();
        if (rate > 1.0e-5f) {
            rotStep.fromAxisAngleRad(angVel.x / rate, angVel.y / rate, angVel.z / rate, rate);
            rotStep.mul(rot, rot);
            rot.normalize();
        }
        if (landed) {
            topple(level);
        }

        light = fullbright ? FULL_BRIGHT : Mc26.lightCoords(level, BlockPos.containing(pos.x, pos.y, pos.z));
    }

    /**
     * Moves with block collisions. The point that collides is the shard's lowest corner, not its centroid: a shard comes
     * to rest on the ground, not half in it (half in it, the part under the ground showed over the blocks, and vanished
     * once the shard settled and was drawn in the world's depth). True if it landed (on the ground this tick).
     */
    private boolean moveWithCollisions(ClientLevel level, float bounce) {
        double below = belowCentroid();
        pos.y -= below;
        int hits = BlockCollision.move(level, pos, vel, 0, bounce, GROUND_FRICTION);
        pos.y += below;
        // Spin damping.
        if ((hits & BlockCollision.HIT_X) != 0) {
            angVel.mul(0.7f);
        }
        if ((hits & BlockCollision.HIT_Z) != 0) {
            angVel.mul(0.7f);
        }
        if ((hits & BlockCollision.LANDED) != 0) {
            angVel.mul(0.5f);
            return true;
        }
        return false;
    }

    /** How far the lowest corner is below the centroid now (world axes, blocks, at least 0). */
    private double belowCentroid() {
        float lowest = 0f;
        for (Vector3f corner : corners) {
            lowest = Math.min(lowest, rot.transform(corner, scratch).y);
        }
        return -lowest;
    }

    /**
     * On the ground: the shard falls over a little more onto whichever face is nearer (as a thin pane does), then is
     * lifted out if its lowest corner went into the block under it. After some ticks it lies flat on the ground.
     */
    private void topple(ClientLevel level) {
        Vector3f facing = rot.transform(normal, scratch);
        float up = Math.clamp(Math.abs(facing.y), 0f, 1f);
        if (up < 0.9999f) {
            facing.cross(facing.y >= 0f ? UP : DOWN, axis);
            float length = axis.length();
            if (length > 1.0e-6f) {
                float angle = (float) Math.acos(up) * TOPPLE;
                rotStep.fromAxisAngleRad(axis.x / length, axis.y / length, axis.z / length, angle);
                rotStep.mul(rot, rot);
                rot.normalize();
            }
        }
        double below = belowCentroid();
        net.minecraft.world.phys.AABB box = BlockCollision.hitBox(level, pos.x, pos.y - below, pos.z);
        if (box != null) {
            double lift = box.maxY + GAP - (pos.y - below);
            if (lift > 0 && lift < 0.5) {
                pos.y += lift;
            }
        }
    }

    static boolean collides(ClientLevel level, double x, double y, double z) {
        return BlockCollision.collides(level, x, y, z);
    }
}
