package net.shattergui.render;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderType;
import net.shattergui.ShatterGuiClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the geometry {@link ShardRenderer} built for this frame, at the end of the level render
 * ({@code WorldRenderEvents.END}: after blocks, entities, translucent blocks, Fabulous' composite, clouds, weather, and
 * before {@code GameRenderer#renderItemInHand}, which clears the depth and draws the hand; then the GUI). Same camera
 * and partial tick as the level.
 *
 * <ul>
 *   <li>{@code "alwaysOnTop"}: the main depth buffer is cleared first, so the pieces depth-test only against each other
 *       and no wall or floor hides them; no fog (like the GUI they came from). The hand, the HUD and screens stay on
 *       top, as on 26.1.</li>
 *   <li>Otherwise: drawn into the world's depth buffer with the level's fog, so blocks in front hide them.</li>
 * </ul>
 * Translucent layers are sorted back to front (vertices are camera-relative: the camera is the origin), batches from the
 * farthest to the nearest (ShardRenderer). At END the level's model-view is already popped, so the camera rotation and
 * the projection (view bobbing included) are those Fabric hands over for this frame.
 */
public final class OnTopPass {
    private record Entry(RenderType type, ShardRenderer.Geometry3d geometry) {
    }

    private static final List<Entry> PENDING = new ArrayList<>();
    private static ByteBufferBuilder buffer;
    private static boolean failed;

    private OnTopPass() {
    }

    /** Level render, before this frame's geometry is added: drops what an earlier frame did not draw. */
    static void begin() {
        PENDING.clear();
    }

    /** Geometry relative to the camera position, in world axes. */
    static void add(RenderType type, ShardRenderer.Geometry3d geometry) {
        PENDING.add(new Entry(type, geometry));
    }

    /** Whether anything waits to be drawn this frame (tests). */
    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /** Draws what was collected, in order, with the level's camera rotation and projection. */
    static void draw(Matrix4f viewRotation, Matrix4f projection, boolean onTop) {
        if (PENDING.isEmpty()) {
            return;
        }
        if (buffer == null) {
            buffer = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            if (onTop) {
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                FogRenderer.setupNoFog();
            }
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.set(viewRotation);
            RenderSystem.applyModelViewMatrix();
            PoseStack.Pose pose = new PoseStack().last();
            for (Entry entry : PENDING) {
                BufferBuilder builder = new BufferBuilder(buffer, entry.type.mode(), entry.type.format());
                entry.geometry.emit(pose, builder);
                MeshData mesh = builder.build();
                if (mesh == null) {
                    continue;
                }
                if (entry.type.sortOnUpload()) {
                    mesh.sortQuads(buffer, VertexSorting.DISTANCE_TO_ORIGIN);
                }
                entry.type.draw(mesh);
            }
        } catch (RuntimeException e) {
            buffer.discard();
            if (!failed) {
                failed = true;
                ShatterGuiClient.LOGGER.warn("Drawing the effects failed; this frame's effects are skipped", e);
            }
        } finally {
            PENDING.clear();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }
    }
}
