package net.shattergui.shatter;

import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.shattergui.capture.CapturePool;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterWave;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.FlyTarget;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.FollowCamera;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class ShardManager {
    public record OpenAnchor(PlaneBasis captured, java.util.function.Supplier<PlaneBasis> now, boolean onSpatialPlane) {
    }

    public static final class Batch {
        final CapturePool.Slot slot;
        final List<ShardInstance> shards;
        final ShatterEffect effect;
        final ShatterWave wave;
        final ParamValues params;
        final boolean preview;
        boolean rendered;
        int hurryLeft = -1;
        int hurryTotal = HURRY_TICKS;
        final OpenAnchor anchor;
        final boolean follows;
        private PlaneBasis lastPlane;
        final Screen owner;

        Batch(CapturePool.Slot slot, List<ShardInstance> shards, ShatterEffect effect, ShatterWave wave,
              ParamValues params, boolean preview, OpenAnchor anchor, Screen owner) {
            this.anchor = anchor;
            this.follows = anchor != null && (effect.opens()
                    ? FollowCamera.follows(params, effect.followCamera(), true)
                    : effect.base() instanceof net.shattergui.effect.FlyEffect
                    && FollowCamera.follows(params, effect.followCamera(), false));
            this.owner = owner;
            this.slot = slot;
            this.shards = shards;
            this.effect = effect;
            this.wave = wave;
            this.params = params;
            this.preview = preview;
        }

        public ParamValues params() {
            return params;
        }

        public boolean preview() {
            return preview;
        }

        public boolean belongsToScreen() {
            return !effect.opens() || owner == null || owner == Mc26.screen();
        }

        public CameraSpace space(Vec3 live) {
            if (follows) {
                PlaneBasis now = anchor.now().get();
                lastPlane = now != null ? now : lastPlane;
                if (lastPlane != null) {
                    return CameraSpace.follow(anchor.captured(), lastPlane);
                }
            }
            return CameraSpace.world(live);
        }

        public org.joml.Vector3d planeCenter() {
            return anchor != null ? anchor.captured().center() : null;
        }

        public boolean follows() {
            return follows;
        }

        public boolean onSpatialPlane() {
            return anchor != null && anchor.onSpatialPlane();
        }

        boolean hurried() {
            return hurryLeft >= 0;
        }

        void hurry(int ticks) {
            if (hurried()) {
                return;
            }
            hurryLeft = ticks;
            hurryTotal = ticks;
            if (wave != null) {
                wave.hurry(ticks);
            }
        }

        public float fade(float partialTick) {
            return hurried() ? Math.clamp((hurryLeft - partialTick) / hurryTotal, 0f, 1f) : 1f;
        }

        public Identifier texture() {
            return slot.id();
        }

        public List<ShardInstance> shards() {
            return shards;
        }

        public ShatterEffect effect() {
            return effect;
        }

        public ShatterWave wave() {
            return wave;
        }

        public boolean markRendered() {
            boolean first = !rendered;
            rendered = true;
            return first;
        }
    }

    private static final int MAX_ACTIVE_WAVES = 4;
    private static final int MAX_TOTAL_SHARDS = 8000;
    private static final int HURRY_TICKS = 6;
    public static final int FINISH_TICKS = 4;

    private static final List<Batch> BATCHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static Long testSeed;
    private static Batch lastSpawned;
    private static long seededWaves;

    private ShardManager() {
    }

    public static Batch lastSpawned() {
        return lastSpawned;
    }

    public static void seedForTests(Long seed) {
        testSeed = seed;
        seededWaves = 0;
    }

    private static boolean settleHeld;

    public static void holdSettledForTests(boolean held) {
        settleHeld = held;
    }

    public static int settledShardCount() {
        int count = 0;
        for (Batch batch : BATCHES) {
            for (ShardInstance shard : batch.shards) {
                if (shard.settled()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static List<Batch> batches() {
        return BATCHES;
    }

    public static int liveShardCount() {
        int count = 0;
        for (Batch batch : BATCHES) {
            count += batch.shards.size();
        }
        return count;
    }

    private static int limitedShardCount() {
        int count = 0;
        for (Batch batch : BATCHES) {
            if (batch.effect.countsTowardShardLimit()) {
                count += batch.shards.size();
            }
        }
        return count;
    }

    public static boolean hasPreview() {
        for (Batch batch : BATCHES) {
            if (batch.preview) {
                return true;
            }
        }
        return false;
    }

    public static int waveCount() {
        return BATCHES.size();
    }

    public static int playingCount() {
        int count = FlyOpen.running() ? 1 : 0;
        for (Batch batch : BATCHES) {
            if (!batch.preview && !batch.hurried()) {
                count++;
            }
        }
        return count;
    }

    public static boolean admit(int waiting, String what) {
        ShatterConfig config = ShatterConfig.get();
        int max = config.maxConcurrent();
        int playing = playingCount() + waiting;
        if (playing < max) {
            return true;
        }
        if (config.skipNewEffects()) {
            skipped++;
            ShatterProfiler.value("budget.skipNew", what + " not started: " + playing + " effect(s) playing, at most " + max);
            return false;
        }
        finishOldest(playing - (max - 1), what, max);
        return true;
    }

    private static void finishOldest(int count, String what, int max) {
        int finished = 0;
        for (Batch batch : BATCHES) {
            if (finished >= count) {
                break;
            }
            if (!batch.preview && !batch.hurried()) {
                batch.hurry(FINISH_TICKS);
                finished++;
            }
        }
        if (finished > 0) {
            ShatterProfiler.value("budget.finishOldest", finished + " older effect(s) finish in " + FINISH_TICKS
                    + " ticks for " + what + " (at most " + max + " at once)");
        }
    }

    private static int peakPlaying;
    private static int skipped;

    public static int skippedCount() {
        return skipped;
    }

    public static int peakPlaying() {
        return peakPlaying;
    }

    public static void resetPeak() {
        peakPlaying = playingCount();
    }

    public static int longestFinishing() {
        int longest = -1;
        for (Batch batch : BATCHES) {
            if (batch.hurried()) {
                longest = Math.max(longest, batch.hurryTotal - batch.hurryLeft);
            }
        }
        return longest;
    }

    public static CapturePool.Slot acquireSlot(int width, int height, TextureFormat format) {
        CapturePool.Slot slot = CapturePool.acquire(width, height, format);
        while (slot == null && !BATCHES.isEmpty()) {
            Batch dropped = BATCHES.removeFirst();
            dispose(dropped);
            CapturePool.release(dropped.slot);
            slot = CapturePool.acquire(width, height, format);
        }
        return slot;
    }

    public static void spawn(CapturePool.Slot slot, ShardPlane plane, ImageRect bounds, Vector3f forward,
                             ShatterEffect effect, FlyTarget target) {
        spawn(slot, plane, bounds, forward, effect, target, ParamValues.current(), false);
    }

    public static void spawn(CapturePool.Slot slot, ShardPlane plane, ImageRect bounds, Vector3f forward,
                             ShatterEffect effect, FlyTarget target, ParamValues params, boolean preview) {
        spawn(slot, plane, bounds, forward, effect, target, params, preview, null, null);
    }

    public static void spawn(CapturePool.Slot slot, ShardPlane plane, ImageRect bounds, Vector3f forward,
                             ShatterEffect effect, FlyTarget target, ParamValues params, boolean preview,
                             OpenAnchor anchor, Screen owner) {
        lastSpawned = null;
        if (effect.opens() && (owner == null || owner != Mc26.screen())) {
            CapturePool.release(slot);
            ShatterProfiler.openValue("open.dropped", "screen no longer open when the gathering would start");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (!ShatterConfig.get().enabled && !preview)) {
            CapturePool.release(slot);
            return;
        }
        if (!preview && !admitAtSpawn(effect)) {
            CapturePool.release(slot);
            return;
        }
        long spawnStart = ShatterProfiler.now();
        params = effect.prepare(params);
        RandomSource random = testSeed != null ? RandomSource.create(testSeed * 31 + seededWaves++) : RANDOM;
        ShardSource source = new ShardSource(plane, bounds, forward, params, mc.level, random, target);
        List<ShardInstance> shards = effect.createShards(source);
        ShatterWave wave = effect.createWave(source, shards);
        ShatterProfiler.step("spawn.effect.createShards [" + effect.id() + "] (mesh + initial motion)", spawnStart);
        if (shards.isEmpty()) {
            CapturePool.release(slot);
            return;
        }
        int maxShards = params.i(Params.MAX_SHARDS);
        if (effect.countsTowardShardLimit()) {
            if (shards.size() > maxShards) {
                if (testSeed != null) {
                    Collections.shuffle(shards, new java.util.Random(random.nextLong()));
                } else {
                    Collections.shuffle(shards);
                }
                shards = new ArrayList<>(shards.subList(0, maxShards));
            }
            enforceLimit(shards.size(), maxShards);
        }
        hurryOldWaves(shards.size());
        lastSpawned = new Batch(slot, shards, effect, wave, params, preview, anchor, effect.opens() ? owner : null);
        BATCHES.add(lastSpawned);
        peakPlaying = Math.max(peakPlaying, playingCount());
        effect.playSound(source);
        ShatterProfiler.step(String.format(Locale.ROOT, "spawn.total (%d shards, GUI %.0f%%x%.0f%% of %dx%d, textures in memory: %d)",
                shards.size(), bounds.width() * 100, bounds.height() * 100, slot.width(), slot.height(),
                CapturePool.texturesWithMemory()), spawnStart);
    }

    private static boolean admitAtSpawn(ShatterEffect effect) {
        ShatterConfig config = ShatterConfig.get();
        int max = config.maxConcurrent();
        int playing = playingCount();
        if (playing < max) {
            return true;
        }
        String what = (effect.opens() ? "opening " : "close ") + effect.id();
        if (config.skipNewEffects()) {
            skipped++;
            if (effect.opens()) {
                OpenAssemble.stop();
            }
            ShatterProfiler.value("budget.skipNew", what + " dropped at its start: " + playing + " effect(s) playing, at most " + max);
            return false;
        }
        finishOldest(playing - (max - 1), what, max);
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        if (BATCHES.isEmpty()) {
            return;
        }
        boolean paused = mc.isPaused();
        BlockCollision.newTick();
        boolean enabled = ShatterConfig.get().enabled;
        Iterator<Batch> batches = BATCHES.iterator();
        while (batches.hasNext()) {
            Batch batch = batches.next();
            if (!enabled && !batch.preview) {
                if (batch.wave != null) {
                    batch.wave.hurry(1);
                }
                dispose(batch);
                CapturePool.release(batch.slot);
                batches.remove();
                continue;
            }
            if (!batch.belongsToScreen()) {
                dispose(batch);
                CapturePool.release(batch.slot);
                batches.remove();
                continue;
            }
            if (paused && !batch.effect.opens()) {
                continue;
            }
            batch.shards.removeIf(shard -> {
                batch.effect.tick(shard, mc.level, batch.params);
                return shard.isDead();
            });
            boolean waveAlive = batch.wave != null && batch.wave.tick(mc.level, batch.shards, batch.params);
            boolean settles = !batch.effect.opens() && !batch.follows() && !batch.preview && !settleHeld;
            for (ShardInstance shard : batch.shards) {
                shard.updateSettled(mc.level, settles);
            }
            if (batch.hurried() && --batch.hurryLeft <= 0) {
                batch.shards.clear();
                waveAlive = false;
            }
            if (batch.shards.isEmpty() && !waveAlive) {
                dispose(batch);
                CapturePool.release(batch.slot);
                batches.remove();
            }
        }
    }

    public static int endOpenings() {
        int dropped = 0;
        Iterator<Batch> it = BATCHES.iterator();
        while (it.hasNext()) {
            Batch batch = it.next();
            if (batch.effect.opens()) {
                dispose(batch);
                CapturePool.release(batch.slot);
                it.remove();
                dropped++;
            }
        }
        return dropped;
    }

    public static int openingCount() {
        int count = 0;
        for (Batch batch : BATCHES) {
            if (batch.effect.opens()) {
                count++;
            }
        }
        return count;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void clear() {
        for (Batch batch : BATCHES) {
            if (batch.wave != null) {
                batch.wave.hurry(1);
            }
            dispose(batch);
            CapturePool.release(batch.slot);
        }
        BATCHES.clear();
    }

    private static void dispose(Batch batch) {
        if (batch.wave != null) {
            batch.wave.dispose();
        }
    }

    private static void hurryOldWaves(int incoming) {
        int waves = 1;
        int shards = incoming;
        for (Batch batch : BATCHES) {
            if (!batch.hurried()) {
                waves++;
                shards += batch.shards.size();
            }
        }
        int hurried = 0;
        for (Batch batch : BATCHES) {
            if (waves <= MAX_ACTIVE_WAVES && shards <= MAX_TOTAL_SHARDS) {
                break;
            }
            if (!batch.hurried()) {
                batch.hurry(HURRY_TICKS);
                waves--;
                shards -= batch.shards.size();
                hurried++;
            }
        }
        if (hurried > 0) {
            ShatterProfiler.value("spam.hurried", hurried + " older wave(s) fade out in " + HURRY_TICKS
                    + " ticks; running now: " + waves + " wave(s), " + shards + " shards");
        }
    }

    private static void enforceLimit(int incoming, int max) {
        int excess = limitedShardCount() + incoming - max;
        if (excess <= 0) {
            return;
        }
        for (Batch batch : BATCHES) {
            if (!batch.effect.countsTowardShardLimit()) {
                continue;
            }
            for (ShardInstance shard : batch.shards) {
                if (excess-- <= 0) {
                    break;
                }
                shard.hurryFade();
            }
        }
        while (limitedShardCount() + incoming > max * 2) {
            Batch oldest = null;
            for (Batch batch : BATCHES) {
                if (batch.effect.countsTowardShardLimit()) {
                    oldest = batch;
                    break;
                }
            }
            if (oldest == null) {
                break;
            }
            BATCHES.remove(oldest);
            dispose(oldest);
            CapturePool.release(oldest.slot);
        }
    }
}
