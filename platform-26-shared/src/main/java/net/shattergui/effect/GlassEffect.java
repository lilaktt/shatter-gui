package net.shattergui.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardSource;

import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class GlassEffect implements ShatterEffect {
    public static final String ID = "glass";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        ParamValues params = source.params();
        int cells = Math.max(1, Math.round(params.i(Params.GLASS_COLUMNS) * params.i(Params.GLASS_ROWS)
                * params.shardScale()));
        ShardMesh.Layout layout = ShardMesh.build(source, cells);
        float burst = params.f(Params.GLASS_BURST) * (params.reduceMotion() ? 0.5f : 1f);
        float spin = params.f(Params.GLASS_SPIN) * (params.reduceMotion() ? 0.3f : 1f);
        for (ShardInstance shard : layout.shards()) {
            ShardMotion.burst(shard, layout, source.forward(), burst, 0.25, source.random());
            ShardMotion.spin(shard, spin, source.random());
        }
        return layout.shards();
    }

    @Override
    public boolean reassemblesOnReopen(ParamValues params) {
        return params.b(Params.GLASS_REASSEMBLE);
    }

    public static void scatterAgain(List<ShardInstance> shards, Vector3d center, Vector3f forward, ParamValues params,
                                    RandomSource random, ClientLevel level) {
        double half = 1.0e-3;
        for (ShardInstance shard : shards) {
            half = Math.max(half, shard.pos().distance(center));
        }
        ShardMesh.Layout layout = new ShardMesh.Layout(shards, center, half);
        float burst = params.f(Params.GLASS_BURST) * (params.reduceMotion() ? 0.5f : 1f);
        float spin = params.f(Params.GLASS_SPIN) * (params.reduceMotion() ? 0.3f : 1f);
        for (ShardInstance shard : shards) {
            int lifetime = Math.max(1, Math.round(params.i(Params.GLASS_LIFETIME) * params.durationScale()
                    * (0.8f + random.nextFloat() * 0.4f)));
            shard.restart(lifetime, params.f(Params.GLASS_FADE_START));
            ShardMotion.burst(shard, layout, forward, burst, 0.25, random);
            ShardMotion.spin(shard, spin, random);
            shard.unstick(level);
        }
    }

    @Override
    public void playSound(ShardSource source) {
        EffectSounds.play(SoundEvents.GLASS_BREAK, source.params().volume(Params.GLASS_SOUND),
                0.9f + source.random().nextFloat() * 0.3f);
    }

    @Override
    public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        shard.tick(level, params.f(Params.GLASS_GRAVITY), params.f(Params.GLASS_AIR_DRAG), params.f(Params.GLASS_BOUNCE),
                params.b(Params.GLASS_FULLBRIGHT), params.fullQuality());
    }

    @Override
    public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
        out.set(ShardMotion.white(shard.alpha(partialTick)));
    }
}
