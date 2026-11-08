package net.shattergui.shatter;

import net.shattergui.platform.impl.Mc26;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ShardInstance {
    public static final int FULL_BRIGHT = 0xF000F0;
    private static final double GROUND_FRICTION = 0.4;
    private static final float TOPPLE = 0.35f;
    private static final double GAP = 1.0e-3;
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f DOWN = new Vector3f(0f, -1f, 0f);
    private final Vector3f scratch = new Vector3f();
    private final Vector3f axis = new Vector3f();

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
    private int stillTicks;
    private boolean settled;
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

    public void setLifetime(int lifetime) {
        this.lifetime = Math.max(1, lifetime);
        this.fadeStart = this.lifetime;
    }

    public boolean settled() {
        return settled;
    }

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

    private boolean moveWithCollisions(ClientLevel level, float bounce) {
        double below = belowCentroid();
        pos.y -= below;
        int hits = BlockCollision.move(level, pos, vel, 0, bounce, GROUND_FRICTION);
        pos.y += below;
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

    private double belowCentroid() {
        float lowest = 0f;
        for (Vector3f corner : corners) {
            lowest = Math.min(lowest, rot.transform(corner, scratch).y);
        }
        return -lowest;
    }

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
