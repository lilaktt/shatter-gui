package net.shattergui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.shattergui.ShatterGuiClient;
import net.shattergui.mixin.RenderPipelinesAccessor;
import net.shattergui.mixin.RenderTypeAccessor;
import net.shattergui.platform.impl.Mc26;

import java.util.HashMap;
import java.util.Map;

public final class ShardRenderTypes {
    public static final RenderPipeline SHARD_PIPELINE = RenderPipelinesAccessor.shatterGui$register(
            Mc26.withSampler1(RenderPipeline.builder(RenderPipelinesAccessor.shatterGui$entitySnippet()))
                    .withLocation(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "pipeline/shard"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                    .withCull(false)
                    .build());

    public static final RenderPipeline MULTIPLY_PIPELINE = RenderPipelinesAccessor.shatterGui$register(
            Mc26.withSampler1(RenderPipeline.builder(RenderPipelinesAccessor.shatterGui$entitySnippet()))
                    .withLocation(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "pipeline/fx_multiply"))
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.DST_COLOR,
                            DestFactor.ONE_MINUS_SRC_ALPHA)))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(Mc26.DEPTH_TEST, false))
                    .build());

    public static final RenderPipeline BACKDROP_PIPELINE = RenderPipelinesAccessor.shatterGui$register(
            RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.GUI_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "pipeline/backdrop"))
                    .withDepthStencilState(java.util.Optional.empty())
                    .withCull(false)
                    .build());
    private static RenderType backdrop;

    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> MULTIPLY_TYPES = new HashMap<>();

    private ShardRenderTypes() {
    }

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

    public static RenderType backdrop() {
        if (backdrop == null) {
            backdrop = RenderTypeAccessor.shatterGui$create("shatter_gui_backdrop",
                    RenderSetup.builder(BACKDROP_PIPELINE).createRenderSetup());
        }
        return backdrop;
    }

    public static RenderType multiply(Identifier texture) {
        return MULTIPLY_TYPES.computeIfAbsent(texture, id -> RenderTypeAccessor.shatterGui$create("shatter_gui_fx_multiply",
                RenderSetup.builder(MULTIPLY_PIPELINE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    public static void forgetMultiply(Identifier texture) {
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
}
