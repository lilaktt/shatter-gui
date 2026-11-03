package net.shattergui.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.ShardInstance;

import java.util.List;

/**
 * Optional per-wave behaviour of an effect (one wave = one closed screen), for things that concern the wave as a whole
 * or outlive its shards, e.g. a particle budget or smoke after everything has burnt.
 */
public interface ShatterWave {
    /**
     * Once per client tick, after the wave's shards were ticked and dead ones removed.
     *
     * @return true to keep the wave alive even if it has no shards left
     */
    boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params);

    /**
     * Too many effects at once (spam): the wave is removed in {@code ticks} ticks and its shards fade out over that
     * time. Anything it owns beyond its shards (particles) must be gone by then too.
     */
    default void hurry(int ticks) {
    }

    /** The wave was removed (ended, dropped, world left): free what it holds. */
    default void dispose() {
    }
}
