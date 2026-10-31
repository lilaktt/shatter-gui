package net.shattergui.custom;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.shattergui.effect.EffectParticles;
import net.shattergui.effect.EffectSounds;
import net.shattergui.effect.ShardTint;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterWave;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardSource;

import java.util.List;

/**
 * A custom effect at run time: the base effect with the custom parameters on top ({@link #prepare}), plus the extra
 * particles and sounds of its definition. Hard caps keep any file from flooding the game: at most
 * {@link #MAX_PER_TICK} extra particles per tick and {@link #MAX_PER_WAVE} per effect.
 */
public final class CustomEffect implements ShatterEffect {
    private static final int MAX_PER_TICK = 12;
    private static final int MAX_PER_WAVE = 300;
    private static final int MAX_BURST = 40;

    private final CustomEffectDef def;
    private final ShatterEffect base;

    public CustomEffect(CustomEffectDef def, ShatterEffect base) {
        this.def = def;
        this.base = base;
    }

    public CustomEffectDef def() {
        return def;
    }

    @Override
    public String id() {
        return def.id();
    }

    @Override
    public ShatterEffect base() {
        return base;
    }

    @Override
    public ParamValues prepare(ParamValues params) {
        return params.with(def.params());
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        return base.createShards(source);
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        ShatterWave inner = base.createWave(source, shards);
        return def.particles().isEmpty() && def.sounds().isEmpty() ? inner : new Extras(inner, source, shards);
    }

    @Override
    public void playSound(ShardSource source) {
        base.playSound(source);
        playSounds(CustomEffectDef.Stage.START, source.params());
    }

    @Override
    public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        base.tick(shard, level, params);
    }

    @Override
    public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
        base.tint(shard, partialTick, params, out);
    }

    @Override
    public boolean countsTowardShardLimit() {
        return base.countsTowardShardLimit();
    }

    private void playSounds(CustomEffectDef.Stage moment, ParamValues params) {
        for (CustomEffectDef.SoundSpec spec : def.sounds()) {
            if (spec.moment() == moment) {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(spec.sound());
                EffectSounds.play(sound, spec.volume() * params.f(Params.SOUND_VOLUME), spec.pitch());
            }
        }
    }

    /** The base wave plus the definition's particles (start, during, end) and end sounds. */
    private final class Extras implements ShatterWave {
        private final ShatterWave inner;
        private final RandomSource random;
        private Vec3 last;
        private int spawned;
        private boolean started;
        private boolean ended;
        private int hurryLeft = -1;

        Extras(ShatterWave inner, ShardSource source, List<ShardInstance> shards) {
            this.inner = inner;
            this.random = source.random();
            this.last = shards.isEmpty() ? Vec3.ZERO : position(shards.getFirst());
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            boolean innerAlive = inner != null && inner.tick(level, liveShards, params);
            if (!liveShards.isEmpty()) {
                last = position(liveShards.get(random.nextInt(liveShards.size())));
            }
            boolean hurried = hurryLeft >= 0 && --hurryLeft <= 0;
            if (!started) {
                started = true;
                burst(CustomEffectDef.Stage.START, params);
            }
            if (!liveShards.isEmpty() && !hurried) {
                during(liveShards, params);
            }
            if (liveShards.isEmpty() && !innerAlive && !ended) {
                ended = true;
                if (hurryLeft < 0) {
                    burst(CustomEffectDef.Stage.END, params);
                    playSounds(CustomEffectDef.Stage.END, params);
                }
            }
            return innerAlive;
        }

        @Override
        public void hurry(int ticks) {
            hurryLeft = ticks;
            if (inner != null) {
                inner.hurry(ticks);
            }
        }

        private void during(List<ShardInstance> liveShards, ParamValues params) {
            int budget = MAX_PER_TICK;
            for (CustomEffectDef.ParticleSpec spec : def.particles()) {
                if (spec.stage() != CustomEffectDef.Stage.DURING) {
                    continue;
                }
                float perTick = spec.density() * params.particleScale();
                int count = (int) perTick + (random.nextFloat() < perTick - (int) perTick ? 1 : 0);
                for (int i = 0; i < count && budget > 0; i++, budget--) {
                    spawn(spec, position(liveShards.get(random.nextInt(liveShards.size()))));
                }
            }
        }

        private void burst(CustomEffectDef.Stage stage, ParamValues params) {
            for (CustomEffectDef.ParticleSpec spec : def.particles()) {
                if (spec.stage() != stage) {
                    continue;
                }
                int count = Math.min(MAX_BURST, Math.round(spec.density() * 4 * params.particleScale()));
                for (int i = 0; i < count; i++) {
                    spawn(spec, last);
                }
            }
        }

        private void spawn(CustomEffectDef.ParticleSpec spec, Vec3 at) {
            if (spawned >= MAX_PER_WAVE || !EffectParticles.allowed()) {
                return;
            }
            spawned++;
            float size = EffectParticles.sizeAt(at);
            EffectParticles.spawn(spec.type(), at, (random.nextDouble() - 0.5) * 0.04, 0.01 + random.nextDouble() * 0.03,
                    (random.nextDouble() - 0.5) * 0.04, size);
        }

        private static Vec3 position(ShardInstance shard) {
            return new Vec3(shard.pos().x, shard.pos().y, shard.pos().z);
        }
    }
}
