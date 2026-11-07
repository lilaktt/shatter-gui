package net.shattergui.effect;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardSource;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * The GUI stays where it is and comes apart into small squares (about 8 GUI pixels each). A noisy wave runs from an
 * edge, a corner or the centre: each square in turn lights up cold white at its edges, comes loose, drifts up and to
 * the side with a little turbulence, shrinks and fades within ~0.6 s. No gravity, no particles.
 *
 * <p>Like burn: a grid of cells, each with its own start time ({@link ShardInstance#effectData}) and stages computed
 * from the time since then (intact, glowing, loose, gone); the glow is the same emissive layer. A square is two
 * triangles; both keep their offset from the square centre in {@link ShardInstance#vel()} and {@link DissolveWave}
 * places them around the drifting centre, scaled, so the square shrinks as one.
 */
public final class DissolveEffect implements ShatterEffect {
    public static final String ID = "dissolve";

    /** More squares than this get bigger instead (a GUI-filling screen at a small GUI scale). */
    private static final int MAX_SQUARES = 2500;
    /** Ticks from coming loose to gone (0.6 s). */
    private static final float LIFE = 12f;
    /** Ticks before and after coming loose that the square glows. */
    private static final float GLOW_SPAN = 2f;
    private static final float GLOW_MAX = 0.55f;
    private static final int GLOW_COLOR = (205 << 16) | (230 << 8) | 255;
    /** Share of the wave spent on the noise that makes its front ragged. */
    private static final float RAGGEDNESS = 0.3f;
    private static final float NOISE_SCALE = 6f;
    private static final int[][] ORIGINS_EDGE = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        ParamValues params = source.params();
        RandomSource random = source.random();
        Window window = Minecraft.getInstance().getWindow();
        // More shards (cinematic preset) = smaller squares.
        float pixels = params.i(Params.DISSOLVE_PIXEL_SIZE) / (float) Math.sqrt(params.shardScale());
        int cols = Math.max(1, Math.round((float) (source.bounds().width() * window.getGuiScaledWidth() / pixels)));
        int rows = Math.max(1, Math.round((float) (source.bounds().height() * window.getGuiScaledHeight() / pixels)));
        if (cols * rows > MAX_SQUARES) {
            double shrink = Math.sqrt((double) cols * rows / MAX_SQUARES);
            cols = Math.max(1, (int) (cols / shrink));
            rows = Math.max(1, (int) (rows / shrink));
        }
        ShardMesh.Layout layout = ShardMesh.buildSquares(source, cols, rows);
        List<ShardInstance> shards = layout.shards();

        float duration = params.i(Params.DISSOLVE_DURATION) * params.durationScale();
        float life = life(params);
        int[] origin = origin(params.s(Params.DISSOLVE_ORIGIN), random);
        int seed = random.nextInt();
        Vector3d center = new Vector3d();
        for (int k = 0; k < shards.size() / 2; k++) {
            float u = (k % cols + 0.5f) / cols;
            float v = (k / cols + 0.5f) / rows;
            float noise = BurnEffect.fbm(u * NOISE_SCALE, v * NOISE_SCALE, seed);
            float release = distance(u, v, origin) * duration * (1f - RAGGEDNESS) + noise * duration * RAGGEDNESS
                    + random.nextFloat() * 0.8f;
            release = Math.clamp(release, 0f, duration);
            ShardInstance a = shards.get(2 * k);
            ShardInstance b = shards.get(2 * k + 1);
            // The two triangles split the square along a diagonal: their centroids average to its centre.
            center.set(a.pos()).add(b.pos()).mul(0.5);
            for (ShardInstance shard : new ShardInstance[]{a, b}) {
                shard.vel().set(shard.pos()).sub(center);
                shard.effectData = release;
                shard.setLifetime(Mth.ceil(release + life) + 1);
            }
        }
        ShatterProfiler.value("dissolve.squares", cols + "x" + rows + " (" + shards.size() + " triangles), wave "
                + Math.round(duration) + " ticks from " + (origin[0] == 0 && origin[1] == 0 ? "centre" : origin[0] + "," + origin[1]));
        return shards;
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        return shards.size() < 2 ? null : new DissolveWave(shards, source, life(source.params()));
    }

    @Override
    public void playSound(ShardSource source) {
        EffectSounds.play(SoundEvents.AMETHYST_BLOCK_CHIME, source.params().volume(Params.DISSOLVE_SOUND),
                1.4f + source.random().nextFloat() * 0.3f);
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
        // Rendering interpolates the position between ticks, i.e. shows time age - 1 + partialTick.
        float since = shard.age() - 1 + partialTick - shard.effectData;
        float t = progress(since, shard.lifetime() - 1 - shard.effectData);
        out.unlit = true;
        out.scale = scale(t);
        out.set((Math.round((1f - smoothstep(0.45f, 1f, t)) * 255) << 24) | 0xFFFFFF);
        if (Math.abs(since) < GLOW_SPAN) {
            out.glow = GLOW_MAX * (1f - Math.abs(since) / GLOW_SPAN);
            out.glowColor = GLOW_COLOR;
        }
    }

    private static float life(ParamValues params) {
        return LIFE * params.durationScale();
    }

    /** 0 while intact, then 0..1 over the drift. */
    static float progress(float since, float life) {
        return since <= 0f ? 0f : Math.clamp(since / Math.max(1f, life), 0f, 1f);
    }

    static float scale(float t) {
        return Mth.lerp(smoothstep(0f, 1f, t), 1f, 0.15f);
    }

    /** {-1, 0, 1} per axis (0, 0 = centre). "random": an edge, a corner or the centre; "edge": an edge or a corner. */
    private static int[] origin(String setting, RandomSource random) {
        if ("center".equals(setting)) {
            return new int[]{0, 0};
        }
        if ("random".equals(setting) && random.nextInt(ORIGINS_EDGE.length + 1) == 0) {
            return new int[]{0, 0};
        }
        return ORIGINS_EDGE[random.nextInt(ORIGINS_EDGE.length)];
    }

    /** 0 at the origin, 1 at the farthest point of the GUI (u, v in 0..1). */
    private static float distance(float u, float v, int[] origin) {
        if (origin[0] == 0 && origin[1] == 0) {
            return (float) (Math.hypot(u - 0.5, v - 0.5) / Math.sqrt(0.5));
        }
        if (origin[0] == 0) {
            return origin[1] < 0 ? v : 1f - v;
        }
        if (origin[1] == 0) {
            return origin[0] < 0 ? u : 1f - u;
        }
        float cu = origin[0] < 0 ? 0f : 1f;
        float cv = origin[1] < 0 ? 0f : 1f;
        return (float) (Math.hypot(u - cu, v - cv) / Math.sqrt(2));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** Moves every loose square: up and to one side, with turbulence, shrinking around its centre. */
    private static final class DissolveWave implements ShatterWave {
        private final ShardInstance[] first;
        private final ShardInstance[] second;
        private final Vector3d[] start;
        private final float[] side;
        private final float[] phase;
        private final float life;
        private final Vector3f up = new Vector3f(0f, 1f, 0f);
        private final Vector3f across;
        private final float rise;
        private final float turbulence;
        private final Vector3d center = new Vector3d();
        private int age;

        DissolveWave(List<ShardInstance> shards, ShardSource source, float life) {
            int squares = shards.size() / 2;
            this.first = new ShardInstance[squares];
            this.second = new ShardInstance[squares];
            this.start = new Vector3d[squares];
            this.side = new float[squares];
            this.phase = new float[squares];
            this.life = life;
            RandomSource random = source.random();
            // Sizes relative to the GUI, so the drift looks the same for the flat GUI and a Spatial GUI plane.
            double size = shards.getFirst().pos().distance(shards.getLast().pos());
            boolean calm = source.params().reduceMotion();
            this.rise = (float) (size * (calm ? 0.12 : 0.25));
            this.turbulence = calm ? 0f : (float) (size * 0.02);
            Vector3f normal = source.plane().normal();
            this.across = new Vector3f(up).cross(normal);
            if (across.lengthSquared() < 1e-4f) {
                across.set(1f, 0f, 0f);
            }
            across.normalize();
            float drift = (float) (size * (calm ? 0.05 : 0.12));
            for (int k = 0; k < squares; k++) {
                first[k] = shards.get(2 * k);
                second[k] = shards.get(2 * k + 1);
                start[k] = new Vector3d(first[k].pos()).sub(first[k].vel());
                side[k] = (random.nextFloat() * 2f - 1f) * drift;
                phase[k] = random.nextFloat() * Mth.TWO_PI;
            }
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            age++;
            for (int k = 0; k < first.length; k++) {
                float since = age - first[k].effectData;
                if (since <= 0f || first[k].age() >= first[k].lifetime()) {
                    continue;
                }
                float t = progress(since, life);
                float lift = rise * (float) Math.pow(t, 1.3);
                float sway = side[k] * t + turbulence * Mth.sin(phase[k] + since * 0.9f);
                float bob = turbulence * 0.5f * Mth.cos(phase[k] * 1.3f + since * 0.7f);
                center.set(start[k])
                        .add(up.x * (lift + bob) + across.x * sway, up.y * (lift + bob) + across.y * sway,
                                up.z * (lift + bob) + across.z * sway);
                float scale = scale(t);
                first[k].pos().set(first[k].vel()).mul(scale).add(center);
                second[k].pos().set(second[k].vel()).mul(scale).add(center);
            }
            return false;
        }
    }
}
