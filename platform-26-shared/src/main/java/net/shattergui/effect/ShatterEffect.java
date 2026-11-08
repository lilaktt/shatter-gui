package net.shattergui.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardSource;

import java.util.List;

public interface ShatterEffect {
    String id();

    List<ShardInstance> createShards(ShardSource source);

    void tick(ShardInstance shard, ClientLevel level, ParamValues params);

    void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out);

    default ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        return null;
    }

    default void playSound(ShardSource source) {
    }

    default ShatterEffect base() {
        return this;
    }

    default ParamValues prepare(ParamValues params) {
        return params;
    }

    default boolean opens() {
        return false;
    }

    default boolean countsTowardShardLimit() {
        return true;
    }

    default Boolean followCamera() {
        return null;
    }
}
