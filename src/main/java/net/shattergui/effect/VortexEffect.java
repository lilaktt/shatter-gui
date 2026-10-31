package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardPlane;
import net.shattergui.shatter.ShardSource;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vortex: the GUI breaks into ~14x9 shards which, with a short wave from the centre outwards, come loose and spiral
 * around a point, faster and closer, stretching a little along their path and turning violet and then white, until
 * they are sucked into the point, shrinking to nothing. Nothing follows: no flash, no particles.
 *
 * <p>The orbit lies in the GUI plane's own axes (right, up, normal) through the target point, in world coordinates, so
 * it works the same for the flat GUI and a Spatial GUI plane. Per shard {@link ShardInstance#effectData} is the tick it
 * comes loose; {@link VortexWave} keeps its starting orbit (radius, angle, height above the plane) and moves it.
 */
public final class VortexEffect implements ShatterEffect {
    public static final String ID = "vortex";

    private static final int CELLS = 14 * 9;
    /** Share of the duration over which the shards come loose, from the centre outwards. */
    private static final float WAVE_SPREAD = 0.25f;
    /** Size at the end of the flight: nothing, the shard vanishes into the point. */
    private static final float END_SCALE = 0f;
    /** Strength of the violet-to-white glow as the shards near the point. */
    private static final float GLOW = 0.7f;
    private static final float MAX_STRETCH = 0.45f;
    private static final int WHITE = 0xFFFFFF;
    private static final int VIOLET = (170 << 16) | (80 << 8) | 255;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        ParamValues params = source.params();
        ShardMesh.Layout layout = ShardMesh.build(source, Math.max(12, Math.round(CELLS * params.shardScale())));
        float duration = duration(params);
        RandomSource random = source.random();
        for (ShardInstance shard : layout.shards()) {
            double fromCenter = shard.pos().distance(layout.center()) / Math.max(1e-6, layout.halfDiagonal());
            float delay = (float) (fromCenter * duration * WAVE_SPREAD) + random.nextFloat() * 1.5f;
            shard.effectData = delay;
            shard.setLifetime(Mth.ceil(delay + flight(duration)) + 1);
        }
        return layout.shards();
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        if (shards.isEmpty()) {
            return null;
        }
        Vector3d center = new Vector3d();
        for (ShardInstance shard : shards) {
            center.add(shard.pos());
        }
        center.div(shards.size());
        Vector3d target = target(source, center);
        ShatterProfiler.value("vortex", String.format(Locale.ROOT, "%d shards, target %s (%.2f blocks from the GUI centre)",
                shards.size(), source.params().s(Params.VORTEX_TARGET), target.distance(center)));
        return new VortexWave(shards, source, target, flight(duration(source.params())));
    }

    @Override
    public void playSound(ShardSource source) {
        EffectSounds.play(SoundEvents.PORTAL_TRIGGER, source.params().volume(Params.VORTEX_SOUND) * 0.25f, 1.8f);
    }

    @Override
    public boolean countsTowardShardLimit() {
        return false;
    }

    @Override
    public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        shard.tickStatic();
    }

    @Override
    public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
        float e = eased(shard.age() - 1 + partialTick - shard.effectData, shard.lifetime() - 1 - shard.effectData);
        int color = e < 0.6f ? lerp(WHITE, VIOLET, e / 0.6f) : lerp(VIOLET, WHITE, (e - 0.6f) / 0.4f);
        float alpha = 1f - smoothstep(0.85f, 1f, e);
        out.unlit = true;
        out.set((Math.round(alpha * 255) << 24) | color);
        out.scale = Mth.lerp(e, 1f, END_SCALE);
        // A violet glow that brightens as the shards near the point.
        out.glow = e * e * GLOW;
        out.glowColor = lerp(VIOLET, WHITE, e);
    }

    static float duration(ParamValues params) {
        return params.i(Params.VORTEX_DURATION) * params.durationScale();
    }

    /** One shard's flight: the release wave takes the rest, so the whole vortex lasts about {@code duration}. */
    static float flight(float duration) {
        return duration * (1f - WAVE_SPREAD);
    }

    /** 0..1 over a shard's flight, accelerating ("faster and closer"). */
    static float eased(float since, float flight) {
        if (since <= 0f) {
            return 0f;
        }
        float t = Math.clamp(since / Math.max(1f, flight), 0f, 1f);
        return (float) Math.pow(t, 1.6);
    }

    /** center: the GUI centre; source: the block or entity it was opened from (else as player); player: in front of the player. */
    private static Vector3d target(ShardSource source, Vector3d center) {
        String mode = source.params().s(Params.VORTEX_TARGET);
        FlyTarget from = source.target();
        if ("center".equals(mode)) {
            return new Vector3d(center);
        }
        if ("source".equals(mode) && from != null && !from.isEdge()) {
            return new Vector3d(from.point().x, from.point().y, from.point().z);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return new Vector3d(center);
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        return new Vector3d(eye.x + look.x * 0.8, eye.y + look.y * 0.8 - 0.2, eye.z + look.z * 0.8);
    }

    private static int lerp(int from, int to, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** Moves every loose shard along its spiral; over when the last one has vanished into the point. */
    private static final class VortexWave implements ShatterWave {
        private record Orbit(double radius, double angle, double height, Vector3f[] corners) {
        }

        private final Map<ShardInstance, Orbit> orbits = new IdentityHashMap<>();
        private final Vector3d target;
        private final Vector3f right;
        private final Vector3f up;
        private final Vector3f normal;
        private final float duration;
        private final float turns;
        private final float pull;
        private final float spin;
        private final boolean stretch;
        private final int end;
        private final Quaternionf step = new Quaternionf();
        private final Vector3f tangent = new Vector3f();
        private int age;

        VortexWave(List<ShardInstance> shards, ShardSource source, Vector3d target, float duration) {
            ParamValues params = source.params();
            ShardPlane plane = source.plane();
            this.target = target;
            this.right = axis(plane.toWorld(1, 0.5).sub(plane.toWorld(0, 0.5)));
            this.up = axis(plane.toWorld(0.5, 0).sub(plane.toWorld(0.5, 1)));
            this.normal = new Vector3f(plane.normal()).normalize();
            this.duration = duration;
            boolean calm = params.reduceMotion();
            this.turns = params.f(Params.VORTEX_TURNS) * (calm ? 0.5f : 1f);
            this.pull = params.f(Params.VORTEX_PULL);
            this.spin = params.f(Params.VORTEX_SPIN) * (calm ? 0.3f : 1f);
            this.stretch = !calm;
            int last = 0;
            for (ShardInstance shard : shards) {
                Vector3d d = new Vector3d(shard.pos()).sub(target);
                double a = d.x * right.x + d.y * right.y + d.z * right.z;
                double b = d.x * up.x + d.y * up.y + d.z * up.z;
                double n = d.x * normal.x + d.y * normal.y + d.z * normal.z;
                Vector3f[] corners = new Vector3f[3];
                for (int k = 0; k < 3; k++) {
                    corners[k] = new Vector3f(shard.corners()[k]);
                }
                orbits.put(shard, new Orbit(Math.hypot(a, b), Math.atan2(b, a), n, corners));
                last = Math.max(last, shard.lifetime());
            }
            this.end = last;
        }

        private static Vector3f axis(Vector3d v) {
            return new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            age++;
            for (ShardInstance shard : liveShards) {
                Orbit orbit = orbits.get(shard);
                float since = age - shard.effectData;
                if (orbit == null || since <= 0f) {
                    continue;
                }
                float e = eased(since, duration);
                double angle = orbit.angle() + turns * Mth.TWO_PI * e;
                double radius = orbit.radius() * Math.pow(1.0 - e, pull);
                double height = orbit.height() * (1.0 - e);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                shard.pos().set(target)
                        .add(right.x * radius * cos + up.x * radius * sin + normal.x * height,
                                right.y * radius * cos + up.y * radius * sin + normal.y * height,
                                right.z * radius * cos + up.z * radius * sin + normal.z * height);
                // Spinning around the plane normal, faster as it closes in.
                step.fromAxisAngleRad(normal.x, normal.y, normal.z, spin * (0.3f + e));
                step.mul(shard.rot(), shard.rot());
                if (stretch) {
                    // Stretched along its path, most in the middle of the flight.
                    tangent.set(right).mul((float) -sin).add(up.x * (float) cos, up.y * (float) cos, up.z * (float) cos);
                    float s = MAX_STRETCH * 4f * e * (1f - e);
                    for (int k = 0; k < 3; k++) {
                        Vector3f base = orbit.corners()[k];
                        float along = base.dot(tangent);
                        shard.corners()[k].set(base).add(tangent.x * along * s, tangent.y * along * s, tangent.z * along * s);
                    }
                }
            }
            return age < end;
        }
    }
}
