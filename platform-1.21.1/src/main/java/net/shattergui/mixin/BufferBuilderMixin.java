package net.shattergui.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.shattergui.capture.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where a captured screen drew ({@link ScreenCapture}): 1.21.1 has no GUI render state to ask for the bounds of what was
 * drawn, so the vertices are watched while the screen is drawn into its slot. GuiGraphics hands them over already
 * transformed by its pose, i.e. in GUI pixels. Both entry points: one vertex at a time and whole baked quads (items).
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {
    @Inject(method = "addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"))
    private void shatterGui$vertex(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> cir) {
        if (ScreenCapture.recordsVertices()) {
            ScreenCapture.drawnAt(x, y);
        }
    }

    @Inject(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"))
    private void shatterGui$bulkVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                                       float nx, float ny, float nz, CallbackInfo ci) {
        if (ScreenCapture.recordsVertices()) {
            ScreenCapture.drawnAt(x, y);
        }
    }
}
