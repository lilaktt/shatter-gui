package net.shattergui.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Invoker("create")
    static RenderType.CompositeRenderType shatterGui$create(String name, VertexFormat format, VertexFormat.Mode mode,
                                                            int bufferSize, boolean affectsCrumbling,
                                                            boolean sortOnUpload, RenderType.CompositeState state) {
        throw new AssertionError();
    }
}
