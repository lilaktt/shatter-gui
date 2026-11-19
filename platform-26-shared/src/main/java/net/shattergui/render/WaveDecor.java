package net.shattergui.render;

import net.minecraft.resources.Identifier;
import net.shattergui.shatter.ShardInstance;

public interface WaveDecor {
    enum Blend {
        NORMAL, ADDITIVE, MULTIPLY
    }

    int overlayCount();

    Identifier overlayTexture(int k);

    Blend overlayBlend(int k);

    boolean overlayUv(ShardInstance shard, int k, float partialTick, float[] out);

    float scale(ShardInstance shard, float partialTick);

    int overlayColor(ShardInstance shard, int k, float partialTick);

    void sprites(float partialTick, SpriteSink sink);

    interface SpriteSink {
        void sprite(Identifier texture, boolean emissive, double x, double y, double z, float halfSize, int argb,
                    boolean settled);
    }
}