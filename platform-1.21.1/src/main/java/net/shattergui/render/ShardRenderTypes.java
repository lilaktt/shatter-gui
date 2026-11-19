package net.shattergui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.shattergui.mixin.RenderTypeAccessor;

import java.util.HashMap;
import java.util.Map;

public final class ShardRenderTypes {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> MULTIPLY_TYPES = new HashMap<>();

    private ShardRenderTypes() {
    }

    public static void init() {
    }

    public static RenderType shard(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_shard",
                DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1536, false, true,
                Shards.shard(id)));
    }

    public static RenderType multiply(ResourceLocation texture) {
        return MULTIPLY_TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_fx_multiply",
                DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1536, false, true,
                Shards.multiply(id)));
    }

    public static void forgetMultiply(ResourceLocation texture) {
        MULTIPLY_TYPES.remove(texture);
    }

    public static int premultiply(int argb, float shade) {
        int a = argb >>> 24;
        float scale = shade * a / 255f;
        int r = Math.round(((argb >> 16) & 0xFF) * scale);
        int g = Math.round(((argb >> 8) & 0xFF) * scale);
        int b = Math.round((argb & 0xFF) * scale);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class Shards extends RenderType {
        private static final ShaderStateShard PARTICLE = new ShaderStateShard(GameRenderer::getParticleShader);
        private static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard(
                "shatter_gui_premultiplied", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                }, () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });
        private static final TransparencyStateShard MULTIPLY = new TransparencyStateShard(
                "shatter_gui_multiply", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                }, () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        private Shards() {
            super("", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 0, false, false, () -> {
            }, () -> {
            });
        }

        static CompositeState shard(ResourceLocation texture) {
            return CompositeState.builder()
                    .setShaderState(PARTICLE)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(PREMULTIPLIED)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false);
        }

        static CompositeState multiply(ResourceLocation texture) {
            return CompositeState.builder()
                    .setShaderState(PARTICLE)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(MULTIPLY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false);
        }
    }
}
