package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardSource;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A code effect (glass, burn, dissolve, vortex, or a schema-1 effect on one of them) played backwards, for opening a
 * screen: its shards are cut as usual, the effect is run ahead of time with particles and sounds muted, and a few
 * moments of that run are kept. The wave then steps through them from the last to the first, so the pieces gather into
 * the interface; the last step is exactly the uncut picture, held one tick, and then the real screen replaces it (and takes input).
 *
 * <p>Long effects are not replayed whole: at most {@link #WINDOW_TICKS} of the forward run (glass
 * {@link #GLASS_WINDOW_TICKS}), shown in {@link net.shattergui.params.OpenTiming} ticks (at most 0.5 s by default),
 * with the pieces fading in over the first part (they may still be in mid-air there).
 * Schema-2 effects have their own backwards run ({@code DataEffect#reversed}).
 */
public final class ReversedEffect implements ShatterEffect {
    /** Ticks of the forward run that are played back. */
    static final int WINDOW_TICKS = 40;
    /** Glass: by then its shards lie on the floor; earlier they are still in the air around the interface. */
    static final int GLASS_WINDOW_TICKS = 22;
    /** Share of the gathering over which pieces fade in. */
    private static final float FADE_IN = 0.3f;
    /** Never record longer than this (an effect that never ends). */
    private static final int MAX_RECORD_TICKS = 400;

    private final ShatterEffect forward;
    private Playback pending;

    public ReversedEffect(ShatterEffect forward) {
        this.forward = forward;
    }

    /** The effect this one plays backwards. */
    public ShatterEffect forward() {
        return forward;
    }

    @Override
    public String id() {
        return forward.id();
    }

    @Override
    public ShatterEffect base() {
        return forward.base();
    }

    @Override
    public ParamValues prepare(ParamValues params) {
        return forward.prepare(params);
    }

    @Override
    public boolean opens() {
        return true;
    }

    @Override
    public boolean countsTowardShardLimit() {
        // A gathered interface with pieces missing would show holes until the screen appears.
        return false;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        long start = ShatterProfiler.now();
        List<ShardInstance> shards = forward.createShards(source);
        ShatterWave wave = forward.createWave(source, shards);
        int window = forward.base() instanceof GlassEffect ? GLASS_WINDOW_TICKS : WINDOW_TICKS;
        pending = Playback.record(forward, wave, shards, source.params(), window);
        ShatterProfiler.step(String.format(Locale.ROOT, "open.record [%s] (%d shards, %d of %d ticks shown in %d)",
                forward.id(), shards.size(), pending.window, pending.recorded, pending.length), start);
        return shards;
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        Playback wave = pending;
        pending = null;
        OpenAssemble.waveStarted(wave.length + 2);
        return wave;
    }

    @Override
    public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        if (shard.effectState instanceof Ref ref) {
            ref.playback.place(shard, ref.index);
        } else {
            shard.tickStatic();
        }
    }

    @Override
    public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
        if (shard.effectState instanceof Ref ref) {
            ref.playback.tint(ref.index, shard.age(), partialTick, out);
        }
    }

    /** Which shard of which playback. */
    private record Ref(Playback playback, int index) {
    }

    /** One shard at one recorded moment. */
    private static final class Frame {
        final double x;
        final double y;
        final double z;
        final Quaternionf rot;
        final int light;
        /** Alpha 0 when the shard was already gone at that moment. */
        final int center;
        final int edge;
        final float scale;
        final boolean unlit;
        final float glow;
        final int glowColor;
        /** Forward time of the moment, for the face shading. */
        final float time;

        Frame(ShardInstance shard, ShardTint tint, boolean alive, float time) {
            x = shard.pos().x;
            y = shard.pos().y;
            z = shard.pos().z;
            rot = new Quaternionf(shard.rot());
            light = shard.light();
            center = alive ? tint.center : 0;
            edge = alive ? tint.edge : 0;
            scale = tint.scale;
            unlit = tint.unlit;
            glow = alive ? tint.glow : 0f;
            glowColor = tint.glowColor;
            this.time = Float.isNaN(tint.lightAge) ? time : tint.lightAge;
        }
    }

    /** The backwards wave: frames[step][shard], step 0 = the latest recorded moment, {@link #length} = the start. */
    private static final class Playback implements ShatterWave {
        final Frame[][] frames;
        final int length;
        final int window;
        final int recorded;
        private boolean done;

        private Playback(Frame[][] frames, int window, int recorded) {
            this.frames = frames;
            this.length = frames.length - 1;
            this.window = window;
            this.recorded = recorded;
        }

        static Playback record(ShatterEffect effect, ShatterWave wave, List<ShardInstance> shards, ParamValues params,
                               int windowTicks) {
            ClientLevel level = Minecraft.getInstance().level;
            int n = shards.size();
            List<Frame[]> moments = new ArrayList<>();
            ShardTint tint = new ShardTint();
            boolean[] dead = new boolean[n];
            List<ShardInstance> live = new ArrayList<>(shards);
            moments.add(snapshot(effect, shards, dead, params, tint, 0));
            int recorded = 0;
            EffectParticles.mute(true);
            try {
                for (int t = 1; t <= MAX_RECORD_TICKS && !live.isEmpty(); t++) {
                    for (int i = 0; i < n; i++) {
                        ShardInstance shard = shards.get(i);
                        if (dead[i]) {
                            continue;
                        }
                        effect.tick(shard, level, params);
                        if (shard.isDead()) {
                            dead[i] = true;
                            live.remove(shard);
                        }
                    }
                    if (wave != null) {
                        wave.tick(level, live, params);
                    }
                    recorded = t;
                    if (t <= windowTicks) {
                        moments.add(snapshot(effect, shards, dead, params, tint, t));
                    } else {
                        break;
                    }
                }
                if (wave != null) {
                    wave.hurry(1);
                    wave.dispose();
                }
            } finally {
                EffectParticles.mute(false);
            }
            int window = moments.size() - 1;
            int length = net.shattergui.params.OpenTiming.playback(window, false, params);
            // Step j shows the forward moment window·(1 − j/length), rounded to a recorded tick.
            Frame[][] frames = new Frame[length + 1][];
            for (int j = 0; j <= length; j++) {
                frames[j] = moments.get(Math.round(window * (1f - j / (float) length)));
            }
            Playback playback = new Playback(frames, window, recorded);
            for (int i = 0; i < n; i++) {
                ShardInstance shard = shards.get(i);
                shard.restart(length + 2);
                shard.effectState = new Ref(playback, i);
                playback.set(shard, i, 0);
                shard.prevPos().set(shard.pos());
                shard.prevRot().set(shard.rot());
            }
            return playback;
        }

        private static Frame[] snapshot(ShatterEffect effect, List<ShardInstance> shards, boolean[] dead,
                                        ParamValues params, ShardTint tint, int time) {
            Frame[] frame = new Frame[shards.size()];
            for (int i = 0; i < frame.length; i++) {
                ShardInstance shard = shards.get(i);
                tint.reset();
                if (!dead[i]) {
                    effect.tint(shard, 0f, params, tint);
                }
                frame[i] = new Frame(shard, tint, !dead[i], time);
            }
            return frame;
        }

        private void set(ShardInstance shard, int index, int step) {
            Frame f = frames[Math.min(step, length)][index];
            shard.pos().set(f.x, f.y, f.z);
            shard.rot().set(f.rot);
            shard.setLight(f.light);
        }

        /** One tick: age + 1, then the shard is at step = age (held at the last one). */
        void place(ShardInstance shard, int index) {
            shard.tickStatic();
            set(shard, index, shard.age());
        }

        void tint(int index, int age, float partialTick, ShardTint out) {
            // The frame interpolates from the previous step to this one.
            Frame a = frames[Math.clamp(age - 1, 0, length)][index];
            Frame b = frames[Math.min(age, length)][index];
            float t = age >= 1 && age <= length ? partialTick : 1f;
            float shown = Math.clamp(age - 1 + partialTick, 0f, length);
            float fadeIn = Math.min(1f, shown / Math.max(1f, length * FADE_IN));
            fadeIn = fadeIn * fadeIn * (3 - 2 * fadeIn);
            out.center = scaleAlpha(lerpColor(a.center, b.center, t), fadeIn);
            out.edge = scaleAlpha(lerpColor(a.edge, b.edge, t), fadeIn);
            out.scale = a.scale + (b.scale - a.scale) * t;
            out.unlit = b.unlit;
            out.glow = (a.glow + (b.glow - a.glow) * t) * fadeIn;
            out.glowColor = b.glowColor != 0 ? b.glowColor : a.glowColor;
            out.lightAge = a.time + (b.time - a.time) * t;
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            if (liveShards.isEmpty() && !done) {
                done = true;
                OpenAssemble.waveDone();
            }
            return false;
        }

        @Override
        public void dispose() {
            if (!done) {
                done = true;
                OpenAssemble.waveDone();
            }
        }

        private static int lerpColor(int a, int b, float t) {
            if (a == b || t >= 1f) {
                return t >= 1f ? b : a;
            }
            int out = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                int ca = (a >>> shift) & 0xFF;
                int cb = (b >>> shift) & 0xFF;
                out |= Math.round(ca + (cb - ca) * t) << shift;
            }
            return out;
        }

        private static int scaleAlpha(int argb, float factor) {
            if (factor >= 1f) {
                return argb;
            }
            return (Math.round((argb >>> 24) * factor) << 24) | (argb & 0xFFFFFF);
        }
    }
}
