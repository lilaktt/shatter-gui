package net.shattergui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Draws one render type's geometry right now (since 26.2 {@code RenderType} no longer draws meshes itself). */
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
            prepared.drawFromBuffer(info);
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
