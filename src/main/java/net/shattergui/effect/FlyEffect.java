package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.modifier.EdgeTransition;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardSource;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * The GUI flies back into the block or entity it was opened from: the whole plane eases in towards it within the fly
 * duration, shrinking to a dot, and ends in a small puff. For a screen opened without one it leaves through an edge of
 * the screen instead ({@link EdgeTransition}). Without any target (never from the mod's own paths) it just shrinks and
 * fades where it is.
 *
 * <p>The plane is cut into a few pieces that move as one rigid plane: each keeps its offset from the plane centre in
 * {@link ShardInstance#vel()} (unused otherwise, nothing here is simulated), and {@link FlyWave} places them every tick.
 * Rendering interpolates between ticks, i.e. it shows time {@code age - 1 + partialTick}; the corner scale in
 * {@link #tint} uses the same time. The flight shape per shard is in {@link ShardInstance#effectData}: 0 = into a
 * point, 1 = out through an edge, 2 = in place.
 */
public final class FlyEffect implements ShatterEffect {
    public static final String ID = "fly";

    /** The plane is not broken, so a handful of pieces is enough. */
    private static final int CELLS = 12;
    /** Size at the container, relative to the GUI's size. */
    private static final float END_SCALE = 0.03f;
    private static final int PUFF_PARTICLES = 6;
    private static final float INTO_POINT = 0f;
    private static final float THROUGH_EDGE = 1f;
    private static final float IN_PLACE = 2f;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        ShardMesh.Layout layout = ShardMesh.build(source, CELLS);
        int duration = source.params().ticks(Params.FLY_DURATION);
        FlyTarget target = source.target();
        float shape = target == null ? IN_PLACE : target.isEdge() ? THROUGH_EDGE : INTO_POINT;
        for (ShardInstance shard : layout.shards()) {
            shard.vel().set(shard.pos()).sub(layout.center());
            shard.effectData = shape;
            // Alive until it has been drawn at the end of the flight (time = duration, see the class comment).
            shard.setLifetime(duration + 1);
        }
        return layout.shards();
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        if (shards.isEmpty()) {
            return null;
        }
        ShardInstance first = shards.getFirst();
        Vector3d center = new Vector3d(first.pos()).sub(first.vel());
        FlyTarget target = source.target();
        Vector3d end;
        if (target == null) {
            end = new Vector3d(center);
        } else if (target.isEdge()) {
            end = target.edge().exitPoint(source.plane(), center);
        } else {
            end = new Vector3d(target.point().x, target.point().y, target.point().z);
        }
        int duration = source.params().ticks(Params.FLY_DURATION);
        ShatterProfiler.value("fly.flight", String.format(Locale.ROOT, "%s, %.2f blocks in %d ticks",
                target == null ? "in place" : target.describe(), end.distance(center), duration));
        return new FlyWave(center, end, duration, target, source.random());
    }

    @Override
    public void playSound(ShardSource source) {
        FlyTarget target = source.target();
        EffectSounds.play(target != null && target.isEdge() ? SoundEvents.UI_TOAST_OUT : SoundEvents.BUNDLE_INSERT,
                source.params().volume(Params.FLY_SOUND), 1f + source.random().nextFloat() * 0.1f);
    }

    @Override
    public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        shard.tickStatic();
    }

    @Override
    public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
        float e = eased(shard.age() - 1 + partialTick, shard.lifetime() - 1);
        out.unlit = true;
        out.scale = scale(e, shard.effectData);
        float alpha = shard.effectData == INTO_POINT ? 1f : fadeOut(e);
        out.set((Math.round(alpha * 255) << 24) | 0xFFFFFF);
    }

    /** Ease-in: slow start, fastest at the end. */
    static float eased(float time, int duration) {
        float t = Math.clamp(time / Math.max(1, duration), 0f, 1f);
        return t * t;
    }

    static float scale(float eased, float shape) {
        if (shape == INTO_POINT) {
            return Mth.lerp(eased, 1f, END_SCALE);
        }
        // Edge exits and in-place fades share the exit shape of the modifier (direction does not matter for it).
        return EXIT_SHAPE.exitScale(eased);
    }

    private static float fadeOut(float eased) {
        return EXIT_SHAPE.exitAlpha(eased);
    }

    /** Scale and fade of an exit do not depend on the direction. */
    private static final EdgeTransition EXIT_SHAPE = new EdgeTransition(0, 0, false);

    /** Places the pieces along the flight every tick; at a container it leaves a puff when they arrive. */
    private static final class FlyWave implements ShatterWave {
        private final Vector3d start;
        private final Vector3d end;
        private final int duration;
        private final boolean puff;
        private final RandomSource random;
        private final Vector3d center = new Vector3d();
        private int age;

        FlyWave(Vector3d start, Vector3d end, int duration, FlyTarget target, RandomSource random) {
            this.start = start;
            this.end = end;
            this.duration = duration;
            this.puff = target != null && !target.isEdge();
            this.random = random;
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            age++;
            if (age > duration) {
                if (puff) {
                    puff(params);
                }
                return false;
            }
            float e = eased(age, duration);
            start.lerp(end, e, center);
            for (ShardInstance shard : liveShards) {
                shard.pos().set(shard.vel()).mul(scale(e, shard.effectData)).add(center);
            }
            return true;
        }

        /** A few small poofs, scaled down when close to the camera like the burn particles. */
        private void puff(ParamValues params) {
            Minecraft mc = Minecraft.getInstance();
            int count = Math.round(PUFF_PARTICLES * params.particleScale());
            if (mc.options.particles().get() == ParticleStatus.MINIMAL || count <= 0) {
                return;
            }
            Vec3 at = new Vec3(end.x, end.y, end.z);
            float size = (float) Math.clamp(mc.gameRenderer.getMainCamera().position().distanceTo(at) / 4.0, 0.15, 1.0) * 0.5f;
            for (int i = 0; i < count; i++) {
                Particle particle = mc.particleEngine.createParticle(ParticleTypes.POOF, at.x, at.y, at.z,
                        (random.nextDouble() - 0.5) * 0.04, random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.04);
                if (particle != null) {
                    particle.scale(size);
                }
            }
        }
    }
}
