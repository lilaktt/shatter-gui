package net.shattergui.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.shattergui.ShatterGuiClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Effects drawn over the world ({@code "alwaysOnTop"}): the geometry is built during the level render as before (same
 * camera, same partial tick), but drawn after the whole world — blocks, translucent blocks, clouds, a shader pack's
 * composite — and before the player's hand and the GUI, so the hand, the HUD and screens stay on top. The pass runs in
 * {@code GameRenderer#renderLevel} right before {@code renderItemInHand}, where vanilla has just cleared the main depth
 * buffer, so the pieces depth-test only against each other; translucent layers are sorted back to front. If the hand
 * is not drawn in a frame, the pass runs before {@code GuiRenderer#render} instead (depth cleared there too). A shader
 * pack has finished by then: the pieces use the mod's own pipeline as without shaders (they are not lit by the pack,
 * like the GUI they came from); Iris draws the hand inside its own pass, so with a shader pack the hand stays under
 * the pieces.
 */
public final class OnTopPass {
    private record Entry(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
    }

    private static final List<Entry> PENDING = new ArrayList<>();
    /** The level's projection of this frame (view bobbing and nausea included) and its camera rotation. */
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static ByteBufferBuilder buffer;
    private static ProjectionMatrixBuffer projectionBuffer;
    private static boolean failed;

    private OnTopPass() {
    }

    /** {@code GameRenderer#renderLevel}: the projection the level is drawn with. */
    public static void levelProjection(Matrix4fc projection) {
        PROJECTION.set(projection);
    }

    /** {@code GameRenderer#renderLevel}: the camera rotation (model-view) the level is drawn with. */
    public static void levelViewRotation(Matrix4fc viewRotation) {
        VIEW_ROTATION.set(viewRotation);
    }

    /** Level render, before this frame's geometry is added: drops what an earlier frame did not draw. */
    static void begin() {
        PENDING.clear();
    }

    /** Geometry relative to the camera position, in world axes, as for {@code submitCustomGeometry}. */
    static void add(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        PENDING.add(new Entry(type, geometry));
    }

    /** Whether anything waits to be drawn this frame (tests). */
    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /**
     * Before the hand ({@code renderItemInHand}), or before {@code GuiRenderer#render} if the hand was not drawn: draws
     * what the level render collected, in order (once per frame), then clears the depth again so
     * the GUI finds it as vanilla left it. {@code noFog}: the fog the GUI is drawn with.
     */
    public static void draw(GpuBufferSlice noFog) {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (buffer == null) {
            buffer = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
            projectionBuffer = new ProjectionMatrixBuffer("shatter_gui on top");
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        Lighting lighting = mc.gameRenderer.getLighting();
        // Whatever lighting comes next (the hand, the GUI) gets the lighting it was going to have.
        GpuBufferSlice lights = RenderSystem.getShaderLights();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            // Vanilla cleared it just before; cleared here too, so the pass never depends on what ran in between.
            encoder.clearDepthTexture(main.getDepthTexture(), 1.0);
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(PROJECTION), ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(noFog);
            lighting.setupFor(Lighting.Entry.LEVEL);
            modelView.set(VIEW_ROTATION);
            PoseStack.Pose pose = new PoseStack().last();
            for (Entry entry : PENDING) {
                BufferBuilder builder = new BufferBuilder(buffer, entry.type.mode(), entry.type.format());
                entry.geometry.render(pose, builder);
                MeshData mesh = builder.build();
                if (mesh == null) {
                    continue;
                }
                if (entry.type.sortOnUpload()) {
                    // Camera-relative vertices: the camera is the origin.
                    mesh.sortQuads(buffer, VertexSorting.DISTANCE_TO_ORIGIN);
                }
                entry.type.draw(mesh);
            }
        } catch (RuntimeException e) {
            buffer.discard();
            if (!failed) {
                failed = true;
                ShatterGuiClient.LOGGER.warn("Drawing effects over the world failed; this frame's effects are skipped", e);
            }
        } finally {
            PENDING.clear();
            modelView.popMatrix();
            RenderSystem.setShaderLights(lights);
            RenderSystem.setShaderFog(fog);
            RenderSystem.restoreProjectionMatrix();
            encoder.clearDepthTexture(main.getDepthTexture(), 1.0);
        }
    }
}
