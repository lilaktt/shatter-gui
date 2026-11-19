package net.shattergui.shatter;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ShardInstance {
    public static final int FULL_BRIGHT = 0xF000F0;
    private static final double GROUND_FRICTION = 0.4;

    final Vector3f[] corners;
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
    public float effectData;
    public Object effectState;
    int fadeStart;
    int light = FULL_BRIGHT;
    final float seed;
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

    public void setLifetime(int lifetime) {
        this.lifetime = Math.max(1, lifetime);
        this.fadeStart = this.lifetime;
    }

    public boolean isDead() {
        return age >= lifetime;
    }

    public void restart(int lifetime) {
        age = 0;
        setLifetime(lifetime);
    }

    public void setLight(int light) {
        this.light = light;
    }

    void hurryFade() {
        if (age < fadeStart) {
            fadeStart = age;
        }
    }

    public float alpha(float partialTick) {
        float t = age + partialTick;
        if (t <= fadeStart) {
            return 1f;
        }
        float span = Math.max(1, lifetime - fadeStart);
        return Math.clamp(1f - (t - fadeStart) / span, 0f, 1f);
    }

    public float progress(float partialTick) {
        return Math.clamp((age + partialTick) / lifetime, 0f, 1f);
    }

    public void tickStatic() {
        prevPos.set(pos);
        prevRot.set(rot);
        age++;
    }

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

        light = fullbright ? FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(pos.x, pos.y, pos.z));
    }

    private void moveWithCollisions(ClientLevel level, float bounce) {
        int hits = BlockCollision.move(level, pos, vel, 0, bounce, GROUND_FRICTION);
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
