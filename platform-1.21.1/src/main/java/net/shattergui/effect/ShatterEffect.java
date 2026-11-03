package net.shattergui.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardSource;

import java.util.List;

/**
 * How a closed screen falls apart. Each effect is its own class, registered in {@link ShatterEffects}. Settings come
 * from {@link ParamValues}, taken once when the effect starts ({@link ShardSource#params()}) and passed to every call.
 */
public interface ShatterEffect {
    /** Name used in the config ("effect", "overrides", "effects.<id>"). */
    String id();

    /** Cuts the captured texture into shards and gives them their initial velocity and spin. */
    List<ShardInstance> createShards(ShardSource source);

    /** Once per client tick for each live shard: physics and any side effects such as particles. */
    void tick(ShardInstance shard, ClientLevel level, ParamValues params);

    /**
     * Once per frame for each shard: the vertex colors (ARGB, multiplied with the texture). The center color is used
     * at the shard's centroid and the edge color at its corners, so effects can color the rim differently. Effects may
     * also set a scale, skip shading or add a glow layer, see {@link ShardTint}.
     */
    void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out);

    /** Optional wave-level behaviour, created right after {@link #createShards}; null for none. */
    default ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        return null;
    }

    /** Right after the shards exist: the effect's start sound, if any (see {@link EffectSounds}). */
    default void playSound(ShardSource source) {
    }

    /** The built-in effect this one behaves as (itself; a custom effect returns its base). */
    default ShatterEffect base() {
        return this;
    }

    /** The settings this effect runs with, from the saved (or previewed) ones; a custom effect puts its own on top. */
    default ParamValues prepare(ParamValues params) {
        return params;
    }

    /** Played backwards to open a screen (its pieces gather into the interface). */
    default boolean opens() {
        return false;
    }

    /** Whether this effect's shards count towards the {@code maxShards} limit (and may be thinned out by it). */
    default boolean countsTowardShardLimit() {
        return true;
    }
}
