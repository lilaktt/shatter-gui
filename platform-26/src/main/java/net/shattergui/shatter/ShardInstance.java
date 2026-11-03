package net.shattergui.shatter;

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

        if (!collide) {
            pos.add(vel);
        } else if (noClip) {
            pos.add(vel);
            noClip = collides(level, pos.x, pos.y, pos.z);
        } else {
            moveWithCollisions(level, bounce);
        }

        float rate = angVel.length();
        if (rate > 1.0e-5f) {
            rotStep.fromAxisAngleRad(angVel.x / rate, angVel.y / rate, angVel.z / rate, rate);
            rotStep.mul(rot, rot);
            rot.normalize();
        }

        light = fullbright ? FULL_BRIGHT : LevelRenderer.getLightCoords(level, BlockPos.containing(pos.x, pos.y, pos.z));
    }

    private void moveWithCollisions(ClientLevel level, float bounce) {
        int hits = BlockCollision.move(level, pos, vel, 0, bounce, GROUND_FRICTION);
        // Spin damping.
        if ((hits & BlockCollision.HIT_X) != 0) {
            angVel.mul(0.7f);
        }
        if ((hits & BlockCollision.HIT_Z) != 0) {
            angVel.mul(0.7f);
        }
        if ((hits & BlockCollision.LANDED) != 0) {
            angVel.mul(0.5f);
        }
    }

    static boolean collides(ClientLevel level, double x, double y, double z) {
        return BlockCollision.collides(level, x, y, z);
    }
}
