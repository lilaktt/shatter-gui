package net.shattergui.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

final class ImmediateDraw {
    private static ByteBufferBuilder buffer;

    private ImmediateDraw() {
    }

    static void draw(RenderType type, PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        if (buffer == null) {
            buffer = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
        }
        BufferBuilder builder = new BufferBuilder(buffer, type.mode(), type.format());
        geometry.render(pose, builder);
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        if (type.sortOnUpload()) {
            mesh.sortQuads(buffer, VertexSorting.DISTANCE_TO_ORIGIN);
        }
        type.draw(mesh);
    }

    static void endFrame() {
    }

    static void discard() {
        if (buffer != null) {
            buffer.discard();
        }
    }
}
