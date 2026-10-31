package net.shattergui.render;

import net.minecraft.resources.Identifier;
import net.shattergui.shatter.ShardInstance;

/**
 * Extra things a wave draws besides its shards (schema-2 effects): texture overlays on the shards and own sprite
 * particles. The renderer asks for them once per frame, after the shards.
 */
public interface WaveDecor {
    /** How an overlay is blended over the shards. */
    enum Blend {
        NORMAL, ADDITIVE, MULTIPLY
    }

    int overlayCount();

    Identifier overlayTexture(int k);

    Blend overlayBlend(int k);

    /** Overlay {@code k}'s texture coordinates for the shard's three corners (6 floats); false to skip the shard. */
    boolean overlayUv(ShardInstance shard, int k, float partialTick, float[] out);

    /** The shard's size this frame (as its tint), so overlays follow it. */
    float scale(ShardInstance shard, float partialTick);

    /** Overlay {@code k}'s colour on the shard, straight ARGB (alpha 0 = not drawn). */
    int overlayColor(ShardInstance shard, int k, float partialTick);

    /** Every sprite particle to draw this frame. */
    void sprites(float partialTick, SpriteSink sink);

    interface SpriteSink {
        /** A camera-facing square: world centre, half size in blocks, straight ARGB. */
        void sprite(Identifier texture, boolean emissive, double x, double y, double z, float halfSize, int argb);
    }
}