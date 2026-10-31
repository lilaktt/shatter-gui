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
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Owns all live shards, grouped by the captured texture they sample. */
public final class ShardManager {
    /** One closed screen: its capture texture plus the shards cut from it. The texture slot is freed with the last shard. */
    public static final class Batch {
        final CapturePool.Slot slot;
        final List<ShardInstance> shards;
        final ShatterEffect effect;
        /** The effect's wave-level behaviour, or null. */
        final ShatterWave wave;
        /** The settings the effect runs with, taken when it started. */
        final ParamValues params;
        /** Started by the editor's preview: runs even while the mod is switched off. */
        final boolean preview;
        /** Whether the batch has been drawn yet (for profiling the first frame). */
        boolean rendered;
        /** Ticks until a hurried batch is gone, or -1 if it runs normally (see {@link #hurry}). */
        int hurryLeft = -1;
        /** The length of the hurry, in ticks. */
        int hurryTotal = HURRY_TICKS;
        /**
         * Camera position the batch is drawn relative to instead of the live camera, or null. Set while a screen is
         * gathered on a Spatial GUI plane: that plane hangs at a fixed offset from the camera (which still moves out to
         * third person), and so do the pieces, so they land where the plane will appear.
         */
        final Vec3 cameraAnchor;
        /** The screen an opening (gathering) wave belongs to, or null for a close. */
        final Screen owner;

        Batch(CapturePool.Slot slot, List<ShardInstance> shards, ShatterEffect effect, ShatterWave wave,
              ParamValues params, boolean preview, Vec3 cameraAnchor, Screen owner) {
            this.cameraAnchor = cameraAnchor;
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

        /** Started by the editor's preview. */
        public boolean preview() {
            return preview;
        }

        /**
         * False for an opening wave whose screen is no longer the open one: it is never drawn (and goes at the next tick).
         * Only the screen on display may have a gathering picture of itself.
         */
        public boolean belongsToScreen() {
            return !effect.opens() || owner == null || owner == Minecraft.getInstance().screen;
        }

        /** The camera position to draw relative to, given the live one (see {@link #cameraAnchor}). */
        public Vec3 camera(Vec3 live) {
            return cameraAnchor != null ? cameraAnchor : live;
        }

        boolean hurried() {
            return hurryLeft >= 0;
        }

        /** Fades the whole batch out within {@code ticks}, its particles included. */
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

        /** 1 normally; for a hurried batch, down to 0 when it is gone. Multiplies the opacity of everything drawn. */
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

        /** The wave-level behaviour, or null. */
        public ShatterWave wave() {
            return wave;
        }

        /** True exactly once: the first time the batch is drawn. */
        public boolean markRendered() {
            boolean first = !rendered;
            rendered = true;
            return first;
        }
    }

    /**
     * Spam protection: with more waves than this running, or more shards in all of them, the oldest fade out within
     * {@link #HURRY_TICKS}, so rapid opening and closing never piles up effects (or their particles).
     */
    private static final int MAX_ACTIVE_WAVES = 4;
    private static final int MAX_TOTAL_SHARDS = 8000;
    private static final int HURRY_TICKS = 6;
    /** "finishOldest": a wave making room for a new one is gone within this many ticks (0.2 s). */
    public static final int FINISH_TICKS = 4;

    private static final List<Batch> BATCHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ShardManager() {
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

    /** Shards that count towards {@code maxShards}. */
    private static int limitedShardCount() {
        int count = 0;
        for (Batch batch : BATCHES) {
            if (batch.effect.countsTowardShardLimit()) {
                count += batch.shards.size();
            }
        }
        return count;
    }

    /** Whether an effect started by the editor's preview is still running. */
    public static boolean hasPreview() {
        for (Batch batch : BATCHES) {
            if (batch.preview) {
                return true;
            }
        }
        return false;
    }

    /** Waves still running (with shards, or only wave-level effects such as smoke). */
    public static int waveCount() {
        return BATCHES.size();
    }

    /**
     * Effects playing now that count towards {@code maxConcurrentEffects}: every wave that is not finishing (an opening,
     * a close, or only the particles a close left) and a fly-in. The editor's previews do not count.
     */
    public static int playingCount() {
        int count = FlyOpen.running() ? 1 : 0;
        for (Batch batch : BATCHES) {
            if (!batch.preview && !batch.hurried()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Room for one more effect, asked when it is about to start; {@code waiting}: effects already on their way (a
     * capture that has not reached its frame yet). "finishOldest": the oldest waves finish within {@link #FINISH_TICKS}
     * until the new one fits, and it starts; "skipNew": false if it does not fit — it must not start. The spam limits
     * ({@link #MAX_ACTIVE_WAVES}, {@link #MAX_TOTAL_SHARDS}) still apply on top.
     */
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

    /** "finishOldest": the {@code count} oldest waves that still play normally finish within {@link #FINISH_TICKS}. */
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

    /** The most effects that played at once since {@link #resetPeak}, counted whenever a wave starts (tests). */
    private static int peakPlaying;
    /** Effects that did not start because of "skipNew" in this session (tests). */
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

    /** Ticks the longest-finishing wave (made room, or over a spam limit) has been finishing, -1 if none (tests). */
    public static int longestFinishing() {
        int longest = -1;
        for (Batch batch : BATCHES) {
            if (batch.hurried()) {
                longest = Math.max(longest, batch.hurryTotal - batch.hurryLeft);
            }
        }
        return longest;
    }

    /** A capture texture slot; drops the oldest wave if every slot is taken. */
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

    /**
     * Cuts the {@code bounds} part of the image in {@code slot} into shards lying on {@code plane}. Takes ownership of
     * the slot. {@code target}: where the fly effect flies to, or null. Runs with the saved settings.
     */
    public static void spawn(CapturePool.Slot slot, ShardPlane plane, ImageRect bounds, Vector3f forward,
                             ShatterEffect effect, FlyTarget target) {
        spawn(slot, plane, bounds, forward, effect, target, ParamValues.current(), false);
    }

    /**
     * As above with given settings (the editor's preview runs unsaved ones). {@code preview}: spawns even with the
     * mod switched off, so the editor can show an effect before it is enabled.
     */
    public static void spawn(CapturePool.Slot slot, ShardPlane plane, ImageRect bounds, Vector3f forward,
                             ShatterEffect effect, FlyTarget target, ParamValues params, boolean preview) {
        spawn(slot, plane, bounds, forward, effect, target, params, preview, null, null);
    }

    /**
     * As above; {@code cameraAnchor}: the batch stays at a fixed offset from the camera (see {@link Batch#camera});
     * {@code owner}: the screen an opening wave gathers (it ends when that screen is no longer shown).
     */
    public static void spawn(CapturePool.Slot slot, ShardPlane plane, ImageRect bounds, Vector3f forward,
                             ShatterEffect effect, FlyTarget target, ParamValues params, boolean preview,
                             Vec3 cameraAnchor, Screen owner) {
        if (effect.opens() && (owner == null || owner != mc().screen)) {
            // The screen closed or changed before its picture was taken: nothing to gather.
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
        ShardSource source = new ShardSource(plane, bounds, forward, params, mc.level, RANDOM, target);
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
                Collections.shuffle(shards);
                shards = new ArrayList<>(shards.subList(0, maxShards));
            }
            enforceLimit(shards.size(), maxShards);
        }
        hurryOldWaves(shards.size());
        BATCHES.add(new Batch(slot, shards, effect, wave, params, preview, cameraAnchor, effect.opens() ? owner : null));
        peakPlaying = Math.max(peakPlaying, playingCount());
        effect.playSound(source);
        ShatterProfiler.step(String.format(Locale.ROOT, "spawn.total (%d shards, GUI %.0f%%x%.0f%% of %dx%d, textures in memory: %d)",
                shards.size(), bounds.width() * 100, bounds.height() * 100, slot.width(), slot.height(),
                CapturePool.texturesWithMemory()), spawnStart);
    }

    /**
     * The limit of effects at once checked again when the wave really starts: effects admitted at the same time (two
     * captures in one frame, a Spatial GUI copy) may not all fit. "skipNew" drops this one (an opening shows its screen
     * at once); "finishOldest" makes room.
     */
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
        // Close effects pause with the world; openings are the interface's own animation and go on (EffectClock).
        boolean paused = mc.isPaused();
        BlockCollision.newTick();
        boolean enabled = ShatterConfig.get().enabled;
        Iterator<Batch> batches = BATCHES.iterator();
        while (batches.hasNext()) {
            Batch batch = batches.next();
            if (!enabled && !batch.preview) {
                // Switched off while it ran: gone at once.
                if (batch.wave != null) {
                    batch.wave.hurry(1);
                }
                dispose(batch);
                CapturePool.release(batch.slot);
                batches.remove();
                continue;
            }
            if (!batch.belongsToScreen()) {
                // An opening whose screen closed or was replaced: gone at once, its texture freed.
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

    /**
     * Ends every opening (gathering) wave at once and frees its texture: the screen it gathers closed or was replaced.
     * Whatever its effect: a gathering never outlives its screen. Returns how many were dropped.
     */
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

    /** Opening waves running now (tests). */
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

    /** Drops every wave at once; their particles are cut short too (leaving the world, switching the mod off). */
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

    /** Before a new wave of {@code incoming} shards is added: hurries the oldest waves out while over a spam limit. */
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

    /** A new wave over the limit makes the oldest shards start fading now; far over the limit drops them. */
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
