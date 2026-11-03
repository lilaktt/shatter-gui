package net.shattergui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.shattergui.ShatterGuiClient;
import net.shattergui.mixin.RenderPipelinesAccessor;
import net.shattergui.mixin.RenderTypeAccessor;

import java.util.HashMap;
import java.util.Map;

/**
 * The captured GUI was drawn onto a transparent target, so its colors are premultiplied by alpha. Shards use a copy of
 * the vanilla {@code entity_translucent} pipeline (same shader, lightmap, alpha cutout, no culling) with premultiplied
 * blending ({@code ONE, ONE_MINUS_SRC_ALPHA}) instead of un-premultiplying on the CPU, and without the shader's diffuse
 * shading. Vertex colors must therefore be shaded and premultiplied, see {@link #premultiply}.
 */
public final class ShardRenderTypes {
    public static final RenderPipeline SHARD_PIPELINE = RenderPipelinesAccessor.shatterGui$register(
            RenderPipeline.builder(RenderPipelinesAccessor.shatterGui$entitySnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "pipeline/shard"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    // Diffuse shading is computed per shard on the CPU (ShardRenderer), so the first frame can match
                    // the unlit GUI exactly; PER_FACE_LIGHTING would also depend on triangle winding.
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                    .withCull(false)
                    .build());

    /**
     * Multiply overlays of schema-2 effects: result = dst · (texture · a + 1 − texture alpha · a), i.e. the texture
     * multiplied in with opacity a, when the vertex colour is the tint premultiplied by a (see DecorRenderer).
     */
    public static final RenderPipeline MULTIPLY_PIPELINE = RenderPipelinesAccessor.shatterGui$register(
            RenderPipeline.builder(RenderPipelinesAccessor.shatterGui$entitySnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "pipeline/fx_multiply"))
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.DST_COLOR,
                            DestFactor.ONE_MINUS_SRC_ALPHA)))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .build());

    /** One render type per capture texture; the texture pool is small, so this stays small. */
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> MULTIPLY_TYPES = new HashMap<>();

    private ShardRenderTypes() {
    }

    /** Forces class init so the pipeline is registered before shaders are loaded and gets precompiled. */
    public static void init() {
    }

    public static RenderType shard(Identifier texture) {
        return TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_shard",
                RenderSetup.builder(SHARD_PIPELINE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    /** A multiply overlay with this texture (effect textures; one type each). */
    public static RenderType multiply(Identifier texture) {
        return MULTIPLY_TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_fx_multiply",
                RenderSetup.builder(MULTIPLY_PIPELINE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    /** Drops the cached types of textures that were released. */
    public static void forgetMultiply(Identifier texture) {
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
}
