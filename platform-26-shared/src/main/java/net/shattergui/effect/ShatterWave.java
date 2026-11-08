package net.shattergui.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.ShardInstance;

import java.util.List;

public interface ShatterWave {
    boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params);

    default void hurry(int ticks) {
    }

    default void dispose() {
    }
}
