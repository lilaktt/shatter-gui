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

/**
 * The captured GUI was drawn onto a transparent target, so its colors are premultiplied by alpha. Shards are drawn with
 * premultiplied blending ({@code ONE, ONE_MINUS_SRC_ALPHA}) instead of un-premultiplying on the CPU.
 *
 * <p>1.21.1 has no render pipelines (26.1 copies {@code entity_translucent} with {@code NO_CARDINAL_LIGHTING}): the
 * type is put together from vanilla state shards around the vanilla {@code particle} shader, which is the same thing
 * without the diffuse light: texture × vertex colour × lightmap, fog, alpha cutout at 0.1 (particle.fsh), no culling.
 * The diffuse shading is computed per shard on the CPU (ShardRenderer), so the first frame can match the unlit GUI.
 * Vertex colors must therefore be shaded and premultiplied, see {@link #premultiply}. Format {@code PARTICLE}: position,
 * uv, colour, light (the renderer's normal and overlay are skipped by the buffer for this format).
 */
public final class ShardRenderTypes {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> MULTIPLY_TYPES = new HashMap<>();

    private ShardRenderTypes() {
    }

    /** Nothing to register ahead on 1.21.1 (the shader is vanilla's); kept for the same start-up as on 26.1. */
    public static void init() {
    }

    public static RenderType shard(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_shard",
                DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1536, false, true,
                Shards.shard(id)));
    }

    /**
     * Multiply overlays of schema-2 effects: result = dst · (texture · a + 1 − texture alpha · a), i.e. the texture
     * multiplied in with opacity a, when the vertex colour is the tint premultiplied by a (see DecorRenderer). No depth
     * write, like 26.1's multiply pipeline. Same particle shader: unlike 26.1's multiply pipeline it drops texels whose
     * alpha × opacity is below 0.1 (every vanilla 1.21.1 shader with a texture and fog does), so overlays fainter than
     * that are not drawn.
     */
    public static RenderType multiply(ResourceLocation texture) {
        return MULTIPLY_TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_fx_multiply",
                DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1536, false, true,
                Shards.multiply(id)));
    }

    /** Drops the cached types of textures that were released. */
    public static void forgetMultiply(ResourceLocation texture) {
        MULTIPLY_TYPES.remove(texture);
    }

    /** ARGB with straight alpha → ARGB with color multiplied by {@code shade} and by alpha. */
    public static int premultiply(int argb, float shade) {
        int a = argb >>> 24;
        float scale = shade * a / 255f;
        int r = Math.round(((argb >> 16) & 0xFF) * scale);
        int g = Math.round(((argb >> 8) & 0xFF) * scale);
        int b = Math.round((argb & 0xFF) * scale);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Only here for the protected state shards of {@link RenderStateShard}; never instantiated. */
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
