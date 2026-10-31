package net.shattergui.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the shard pipeline reuse the vanilla entity snippet and be precompiled with the vanilla pipelines. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("ENTITY_SNIPPET")
    static RenderPipeline.Snippet shatterGui$entitySnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline shatterGui$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
