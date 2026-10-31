package net.shattergui.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardSource;

import java.util.List;

/** The original effect: shards burst outward, spin, fall, bounce off blocks and fade. */
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
        // reduceMotion: a gentler burst and much less spin.
        float burst = params.f(Params.GLASS_BURST) * (params.reduceMotion() ? 0.5f : 1f);
        float spin = params.f(Params.GLASS_SPIN) * (params.reduceMotion() ? 0.3f : 1f);
        for (ShardInstance shard : layout.shards()) {
            ShardMotion.burst(shard, layout, source.forward(), burst, 0.25, source.random());
            ShardMotion.spin(shard, spin, source.random());
        }
        return layout.shards();
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
