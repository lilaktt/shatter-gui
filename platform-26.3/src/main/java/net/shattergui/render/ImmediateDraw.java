package net.shattergui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.shattergui.platform.impl.Mc26;

import java.util.Optional;
import java.util.OptionalDouble;

/** Draws one render type's geometry right now (26.3: in a render pass of its own on the main target). */
final class ImmediateDraw {
    private static StagedVertexBuffer staged;

    private ImmediateDraw() {
    }

    static void draw(RenderType type, PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        if (staged == null) {
            staged = new StagedVertexBuffer(() -> "shatter_gui", RenderType.SMALL_BUFFER_SIZE);
        }
        PreparedRenderType prepared = type.prepare();
        // Camera-relative vertices: the camera is the origin.
        VertexSorting sorting = type.sortOnUpload() ? VertexSorting.DISTANCE_TO_ORIGIN : null;
        StagedVertexBuffer.Draw draw = staged.appendDraw(type.format(), type.primitiveTopology(), sorting);
        geometry.render(pose, staged.getVertexBuilder(draw));
        staged.upload();
        StagedVertexBuffer.ExecuteInfo info = staged.getExecuteInfo(draw);
        if (info != null) {
            RenderTarget target = Mc26.mainTarget();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "shatter_gui",
                    target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                prepared.drawFromBuffer(info, pass);
            }
        }
        staged.endDraw();
    }

    /** After a failure while vertices were being written. */
    static void discard() {
        if (staged != null) {
            staged.endDraw();
        }
    }

    /** Once per frame: the buffers of the frame go back to their pools. */
    static void endFrame() {
        if (staged != null) {
            staged.endFrame();
        }
    }
}
