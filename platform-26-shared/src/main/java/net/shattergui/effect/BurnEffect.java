package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.mixin.SingleQuadParticleAccessor;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardSource;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class BurnEffect implements ShatterEffect {
    public static final String ID = "burn";

    private static final int CELLS = 48 * 30;
    private static final float PREHEAT = 5f;
    private static final float EMBER = 3f;
    private static final float CHAR = 7f;
    private static final float CRUMBLE = 7f;
    private static final float BURNT = EMBER + CHAR + CRUMBLE;
    private static final float CENTER_LEAD = 0.5f;
    private static final float CLIMB = 0.8f;
    private static final float RAGGEDNESS = 0.45f;
    private static final float NOISE_SCALE = 5f;

    private static final int WHITE = rgb(255, 255, 255);
    private static final int SCORCHED = rgb(255, 226, 180);
    private static final int EMBER_HOT = rgb(255, 236, 150);
    private static final int EMBER_COOL = rgb(255, 140, 40);
    private static final int CHARCOAL = rgb(34, 24, 18);
    private static final int ASH = rgb(70, 64, 58);
    private static final int GLOW_HOT = rgb(255, 220, 90);
    private static final int GLOW_COOL = rgb(255, 110, 20);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        ParamValues params = source.params();
        ShardMesh.Layout layout = ShardMesh.build(source, Math.max(200, Math.round(CELLS * params.shardScale())));
        RandomSource random = source.random();
        float duration = params.i(Params.BURN_DURATION) * params.durationScale();
        int seed = random.nextInt();
        for (ShardInstance shard : layout.shards()) {
            float[] uv = shard.uv();
            float x = (uv[0] + uv[2] + uv[4]) / 3f;
            float y = 1f - (uv[1] + uv[3] + uv[5]) / 3f;
            float noise = fbm(x * NOISE_SCALE, y * NOISE_SCALE, seed);
            float ignition = (1f - y) * duration * CLIMB + (noise - 0.5f) * duration * RAGGEDNESS
                    + (random.nextFloat() - 0.5f);
            shard.effectData = Math.clamp(ignition, 0f, duration);
            shard.setLifetime(Mth.ceil(shard.effectData + BURNT) + 1);
        }
        ShatterProfiler.value("burn.cells", layout.shards().size() + " triangles, front " + (int) duration + " ticks");
        return layout.shards();
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        return new BurnWave(shards, source.random());
    }

    @Override
    public void playSound(ShardSource source) {
        EffectSounds.play(SoundEvents.FIRECHARGE_USE, source.params().volume(Params.BURN_SOUND) * 0.7f,
                0.8f + source.random().nextFloat() * 0.2f);
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
        float burning = shard.age() + partialTick - shard.effectData;
        out.unlit = true;
        out.edge = color(burning);
        out.center = (out.edge & 0xFF000000) | (color(burning + CENTER_LEAD) & 0xFFFFFF);
        out.scale = scale(burning);
        float flicker = params.reduceMotion() ? 1f
                : 0.8f + 0.2f * Mth.sin((shard.age() + partialTick) * 1.7f + shard.seed() * 30f);
        out.glow = glow(burning) * flicker;
        out.glowColor = lerp(GLOW_HOT, GLOW_COOL, Math.clamp(burning / (EMBER + CHAR), 0f, 1f));
    }

    private static int color(float burning) {
        if (burning < -PREHEAT) {
            return 0xFF000000 | WHITE;
        }
        if (burning < 0f) {
            float heat = 1f + burning / PREHEAT;
            return 0xFF000000 | lerp(WHITE, SCORCHED, heat * heat);
        }
        if (burning < EMBER) {
            return 0xFF000000 | lerp(EMBER_HOT, EMBER_COOL, burning / EMBER);
        }
        if (burning < EMBER + CHAR) {
            return 0xFF000000 | lerp(EMBER_COOL, CHARCOAL, smoothstep(0f, 0.45f, (burning - EMBER) / CHAR));
        }
        float crumble = (burning - EMBER - CHAR) / CRUMBLE;
        int alpha = Math.round((1f - smoothstep(0f, 0.8f, crumble)) * 255);
        return (alpha << 24) | lerp(CHARCOAL, ASH, Math.clamp(crumble, 0f, 1f));
    }

    private static float scale(float burning) {
        if (burning < EMBER + CHAR) {
            return 1f;
        }
        return 1f - 0.9f * smoothstep(0f, 1f, (burning - EMBER - CHAR) / CRUMBLE);
    }

    private static float glow(float burning) {
        if (burning < -1f || burning >= EMBER + CHAR) {
            return 0f;
        }
        if (burning < 0f) {
            return burning + 1f;
        }
        if (burning < EMBER) {
            return 1f;
        }
        return 1f - smoothstep(0f, 0.3f, (burning - EMBER) / CHAR);
    }

    private static final class BurnWave implements ShatterWave {
        private static final int SMOKE_COLUMNS = 6;
        private static final double FULL_SIZE_DISTANCE = 4.0;
        private static final float MIN_SIZE = 0.15f;
        private static final float MIN_SPARK_SIZE = 0.5f;
        private static final float FLAMES_PER_TICK = 2.5f;
        private static final float EMBERS_PER_TICK = 2f;
        private static final float SPARKS_PER_TICK = 0.3f;
        private static final float FRONT_SMOKE_PER_TICK = 0.5f;
        private static final float SMOKE_PER_TICK = 0.125f;
        private static final float SMOKE_REFERENCE = 0.35f;
        private static final int MAX_SMOKE = 24;
        private static final float SMOKE_SIZE = 0.6f;
        private static final int MIN_PUFF_TICKS = 25;
        private static final int FADE_TICKS = 20;

        private record Column(Vector3d pos, float ignition) {
        }

        private record Tracked(SingleQuadParticle particle, int death, float alpha, boolean smoke) {
        }

        private final RandomSource random;
        private final List<Column> columns = new ArrayList<>();
        private final List<ShardInstance> burning = new ArrayList<>();
        private final List<ShardInstance> front = new ArrayList<>();
        private final List<Tracked> particles = new ArrayList<>();
        private int liveSmoke;
        private int hurryEnd = Integer.MAX_VALUE;
        private int fadeTicks = FADE_TICKS;
        private final Vector3f corner = new Vector3f();
        private final Vec3 center;
        private final float burnEnd;
        private int smokeEnd;
        private float size = 1f;
        private int age;

        BurnWave(List<ShardInstance> shards, RandomSource random) {
            this.random = random;
            float lastIgnition = 0f;
            Vector3d sum = new Vector3d();
            for (ShardInstance shard : shards) {
                lastIgnition = Math.max(lastIgnition, shard.effectData);
                sum.add(shard.pos());
            }
            sum.div(Math.max(1, shards.size()));
            this.center = new Vec3(sum.x, sum.y, sum.z);
            this.burnEnd = lastIgnition + BURNT;
            for (int i = 0; i < SMOKE_COLUMNS && !shards.isEmpty(); i++) {
                ShardInstance shard = shards.get(random.nextInt(shards.size()));
                columns.add(new Column(new Vector3d(shard.pos()), shard.effectData));
            }
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            age++;
            smokeEnd = Math.min(hurryEnd,
                    Mth.ceil(burnEnd) + Math.round(params.i(Params.BURN_SMOKE_DURATION) * params.durationScale()));
            boolean emitting = hurryEnd == Integer.MAX_VALUE;
            if (emitting && age == Mth.ceil(burnEnd)) {
                EffectSounds.play(SoundEvents.FIRE_EXTINGUISH, params.volume(Params.BURN_SOUND) * 0.25f, 1.3f);
            }
            burning.clear();
            front.clear();
            for (ShardInstance shard : liveShards) {
                float since = age - shard.effectData;
                if (since >= -0.5f && since < EMBER + CHAR) {
                    burning.add(shard);
                    if (since >= 0f && since < EMBER) {
                        front.add(shard);
                    }
                }
            }
            particles.removeIf(tracked -> !tracked.particle().isAlive());
            liveSmoke = 0;
            for (Tracked tracked : particles) {
                if (tracked.smoke()) {
                    liveSmoke++;
                }
            }

            Minecraft mc = Minecraft.getInstance();
            ParticleStatus status = mc.options.particles().get();
            double distance = Mc26.camera().position().distanceTo(center);
            size = (float) Math.clamp(distance / FULL_SIZE_DISTANCE, MIN_SIZE, 1.0);

            if (emitting && status != ParticleStatus.MINIMAL) {
                float density = params.f(Params.BURN_FLAME_DENSITY) * params.particleScale()
                        * (status == ParticleStatus.DECREASED ? 0.5f : 1f);
                emit(burning, ParticleTypes.FLAME, FLAMES_PER_TICK * density, 0.01, 0.01, 0.015);
                emit(burning, ParticleTypes.SMALL_FLAME, EMBERS_PER_TICK * density, 0.02, 0.04, 0.05);
                if (size >= MIN_SPARK_SIZE) {
                    emit(burning, ParticleTypes.LAVA, SPARKS_PER_TICK * density, 0, 0, 0);
                }
            }
            float smoke = params.f(Params.BURN_SMOKE_DENSITY) / SMOKE_REFERENCE * params.particleScale()
                    * (status == ParticleStatus.DECREASED ? 0.5f : 1f);
            if (emitting && status != ParticleStatus.MINIMAL) {
                emit(front, ParticleTypes.SMOKE, FRONT_SMOKE_PER_TICK * smoke, 0.005, 0.02, 0.02);
            }

            int smokeStop = smokeEnd - MIN_PUFF_TICKS;
            for (Column column : columns) {
                if (age < column.ignition() + EMBER || age >= smokeStop) {
                    continue;
                }
                float intensity = age < burnEnd ? 1f : 1f - (age - burnEnd) / Math.max(1f, smokeStop - burnEnd);
                Vector3d pos = column.pos();
                if (random.nextFloat() < SMOKE_PER_TICK * smoke * intensity) {
                    double spread = 0.15 * size;
                    spawn(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x + jitter(spread), pos.y + jitter(spread),
                            pos.z + jitter(spread), 0, 0.035 + random.nextDouble() * 0.02, 0, SMOKE_SIZE);
                }
            }
            fadeOut();
            return age < smokeEnd || !particles.isEmpty();
        }

        @Override
        public void hurry(int ticks) {
            hurryEnd = age + ticks;
            fadeTicks = Math.max(1, ticks);
            particles.replaceAll(tracked -> {
                int over = tracked.death() - hurryEnd;
                if (over <= 0) {
                    return tracked;
                }
                tracked.particle().setLifetime(Math.max(1, tracked.particle().getLifetime() - over));
                return new Tracked(tracked.particle(), hurryEnd, tracked.alpha(), tracked.smoke());
            });
        }

        private void fadeOut() {
            for (Tracked tracked : particles) {
                int left = tracked.death() - age;
                if (left < fadeTicks) {
                    SingleQuadParticleAccessor quad = (SingleQuadParticleAccessor) tracked.particle();
                    float faded = tracked.alpha() * Math.max(0, left) / fadeTicks;
                    quad.shatterGui$setAlpha(Math.min(quad.shatterGui$getAlpha(), faded));
                }
            }
        }

        private void emit(List<ShardInstance> cells, SimpleParticleType type, float perTick,
                          double sideways, double riseMin, double riseExtra) {
            if (cells.isEmpty() || perTick <= 0f) {
                return;
            }
            int count = (int) perTick + (random.nextFloat() < perTick - (int) perTick ? 1 : 0);
            for (int i = 0; i < count; i++) {
                ShardInstance cell = cells.get(random.nextInt(cells.size()));
                corner.set(cell.corners()[random.nextInt(3)]).mul(random.nextFloat());
                spawn(type, cell.pos().x + corner.x, cell.pos().y + corner.y, cell.pos().z + corner.z,
                        jitter(sideways), riseMin + random.nextDouble() * riseExtra, jitter(sideways));
            }
        }

        private void spawn(SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz) {
            spawn(type, x, y, z, vx, vy, vz, 1f);
        }

        private void spawn(SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz,
                           float extraScale) {
            boolean smoke = type == ParticleTypes.SMOKE || type == ParticleTypes.CAMPFIRE_COSY_SMOKE;
            if (smoke && liveSmoke >= MAX_SMOKE || EffectParticles.muted()) {
                return;
            }
            Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z,
                    vx * size, vy * size, vz * size);
            if (particle == null) {
                return;
            }
            if (smoke) {
                liveSmoke++;
            }
            float scale = size * extraScale;
            if (scale != 1f) {
                particle.scale(scale);
            }
            int left = Math.max(1, smokeEnd - age);
            if (particle.getLifetime() > left) {
                particle.setLifetime(left);
            }
            if (particle instanceof SingleQuadParticle quad) {
                particles.add(new Tracked(quad, age + particle.getLifetime(),
                        ((SingleQuadParticleAccessor) quad).shatterGui$getAlpha(), smoke));
            }
        }

        private double jitter(double amount) {
            return (random.nextDouble() * 2 - 1) * amount;
        }
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return rgb(r, g, b);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    static float fbm(float x, float y, int seed) {
        return 0.65f * valueNoise(x, y, seed) + 0.35f * valueNoise(x * 2f, y * 2f, seed + 1);
    }

    private static float valueNoise(float x, float y, int seed) {
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        float fx = smoothstep(0f, 1f, x - x0);
        float fy = smoothstep(0f, 1f, y - y0);
        float top = Mth.lerp(fx, hash(x0, y0, seed), hash(x0 + 1, y0, seed));
        float bottom = Mth.lerp(fx, hash(x0, y0 + 1, seed), hash(x0 + 1, y0 + 1, seed));
        return Mth.lerp(fy, top, bottom);
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }
}
