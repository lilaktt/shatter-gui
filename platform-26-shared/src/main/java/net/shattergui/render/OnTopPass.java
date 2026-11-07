package net.shattergui.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.shattergui.ShatterGuiClient;
import net.shattergui.compat.iris.IrisCompat;
import net.shattergui.fly.OpenBackdrop;
import net.shattergui.platform.impl.Mc26;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Effects drawn over the world instead of in it. The geometry is built during the level render as before (same camera,
 * same partial tick) or, for openings, after the GUI; it is drawn at two points of {@code GameRenderer#render}:
 *
 * <ul>
 *   <li>{@link #drawSettled}: the settled pieces of the closes ({@link net.shattergui.shatter.Settle}), right before
 *       vanilla clears the depth for the hand: the depth buffer still holds the world, so they are depth-tested against
 *       it (a wall or the block they lie under hides them), with the same pipeline and matrices as the pieces over the
 *       world, so a piece looks the same in both. They are moved {@code Settle.BIAS} towards the camera along the line
 *       of sight (no flicker with the face they lie on, no shift on the screen).</li>
 *   <li>{@link #drawAfterLevel}: after the whole level — blocks, translucent blocks, clouds, a shader pack's composite,
 *       the player's hand — and before the GUI. First the darkened world of a screen that just closed, fading out
 *       ({@link OpenBackdrop}), then the closes ({@code "alwaysOnTop"}). The depth buffer still holds the hand there
 *       (vanilla clears it before the hand and again after this point), so the pieces depth-test against the hand and
 *       each other: the hand stays over them while the fading darkness covers the hand like vanilla's did. A shader
 *       pack draws the hand in its own pass; with one the depth is cleared first (the hand then stays under the
 *       pieces, as before).</li>
 *   <li>{@link #drawOpenings}: after the GUI, so the pieces of a screen being gathered lie over the HUD and over its
 *       darkened background, as the screen will. Flat GUI pieces use the level's projection without view bobbing (the
 *       screen does not bob); pieces on Spatial GUI's plane the level's own (its plane does).</li>
 * </ul>
 *
 * <p>Both use the mod's own pipelines: the pieces are not lit by a shader pack, like the GUI they came from.
 */
public final class OnTopPass {
    /** Geometry relative to the camera position, in world axes, as for {@code submitCustomGeometry}. */
    public record Entry(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
    }

    private static final List<Entry> PENDING = new ArrayList<>();
    /** Settled pieces of this frame: drawn in the world's depth ({@link #drawSettled}). */
    private static final List<Entry> SETTLED = new ArrayList<>();
    /**
     * The level's projection of this frame (view bobbing and nausea included) and its camera rotation as the level was
     * drawn with it; the same without view bobbing (flat openings: the screen does not bob). Vanilla puts the bobbing
     * into the projection; with a shader pack Iris leaves the projection clean and multiplies the camera rotation by it
     * instead ({@code MixinModelViewBobbing}, in place, during the level render), so the rotation is read after the level.
     */
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f CLEAN_PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static final Matrix4f CLEAN_VIEW_ROTATION = new Matrix4f();
    private static ProjectionMatrixBuffer projectionBuffer;
    private static ProjectionMatrixBuffer screenBuffer;
    private static boolean failed;

    private OnTopPass() {
    }

    /** {@code GameRenderer#renderLevel}: the projection the level is drawn with. */
    public static void levelProjection(Matrix4fc projection) {
        PROJECTION.set(projection);
    }

    /** {@code GameRenderer#renderLevel}: the camera's projection before view bobbing and nausea are added. */
    public static void cleanProjection(Matrix4fc projection) {
        CLEAN_PROJECTION.set(projection);
    }

    /**
     * After the level render: the camera rotation (model-view) the level was drawn with, view bobbing included when a
     * shader pack moved it there.
     */
    public static void levelViewRotation(Matrix4fc viewRotation) {
        VIEW_ROTATION.set(viewRotation);
    }

    /** {@code GameRenderer#renderLevel}, before the level render: the camera rotation without view bobbing. */
    public static void cleanViewRotation(Matrix4fc viewRotation) {
        CLEAN_VIEW_ROTATION.set(viewRotation);
        VIEW_ROTATION.set(viewRotation);
    }

    /** Level render, before this frame's geometry is added: drops what an earlier frame did not draw. */
    static void begin() {
        PENDING.clear();
        SETTLED.clear();
    }

    static void add(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        PENDING.add(new Entry(type, geometry));
    }

    static void addSettled(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        SETTLED.add(new Entry(type, geometry));
    }

    /** Whether anything waits to be drawn this frame (tests). */
    public static boolean hasPending() {
        return !PENDING.isEmpty() || !SETTLED.isEmpty();
    }

    /**
     * Before vanilla clears the depth for the hand (the depth buffer holds the world): the settled pieces, depth-tested
     * against the world. Vanilla clears the depth right after, so it is left as it is.
     */
    public static void drawSettled(GpuBufferSlice noFog) {
        if (SETTLED.isEmpty()) {
            return;
        }
        try {
            draw(SETTLED, PROJECTION, VIEW_ROTATION, false, false, noFog);
        } finally {
            SETTLED.clear();
        }
    }

    /**
     * After the level (and the hand), before the GUI: the fading darkness of a closed screen, then what the level render
     * collected. Once per frame. {@code noFog}: the fog the GUI is drawn with.
     */
    public static void drawAfterLevel(GpuBufferSlice noFog) {
        int closing = OpenBackdrop.closingKind();
        if (!SETTLED.isEmpty()) {
            // The point before the hand was not reached this frame: over the world like the rest, not lost.
            PENDING.addAll(0, SETTLED);
            SETTLED.clear();
        }
        if (PENDING.isEmpty() && closing == 0) {
            return;
        }
        try {
            if (closing != 0) {
                drawBackdrop(closing, OpenBackdrop.closeFactor());
            }
            // With a shader pack the hand is not in this depth buffer, but other leftovers may be.
            draw(PENDING, PROJECTION, VIEW_ROTATION, IrisCompat.shaderPackInUse(), true, noFog);
        } finally {
            PENDING.clear();
        }
    }

    /** After the GUI: the openings ({@code flat}: without view bobbing). */
    public static void drawOpenings(List<Entry> entries, boolean flat, GpuBufferSlice noFog) {
        draw(entries, flat ? CLEAN_PROJECTION : PROJECTION, flat ? CLEAN_VIEW_ROTATION : VIEW_ROTATION, true, true, noFog);
    }

    /** {@code clearFirst}: depth-tested only among themselves; {@code clearAfter}: the depth as vanilla left it. */
    private static void draw(List<Entry> entries, Matrix4f projection, Matrix4f viewRotation, boolean clearFirst,
                             boolean clearAfter, GpuBufferSlice noFog) {
        if (entries.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = Mc26.mainTarget();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("shatter_gui on top");
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        Lighting lighting = Mc26.lighting();
        // Whatever lighting comes next (the GUI) gets the lighting it was going to have.
        GpuBufferSlice lights = RenderSystem.getShaderLights();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            if (clearFirst) {
                encoder.clearDepthTexture(main.getDepthTexture(), Mc26.DEPTH_FAR);
            }
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(noFog);
            lighting.setupFor(Lighting.Entry.LEVEL);
            modelView.set(viewRotation);
            PoseStack.Pose pose = new PoseStack().last();
            for (Entry entry : entries) {
                ImmediateDraw.draw(entry.type, pose, entry.geometry);
            }
        } catch (RuntimeException e) {
            ImmediateDraw.discard();
            if (!failed) {
                failed = true;
                ShatterGuiClient.LOGGER.warn("Drawing effects over the world failed; this frame's effects are skipped", e);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.setShaderLights(lights);
            RenderSystem.setShaderFog(fog);
            RenderSystem.restoreProjectionMatrix();
            if (clearAfter) {
                // The GUI finds the depth as vanilla left it.
                encoder.clearDepthTexture(main.getDepthTexture(), Mc26.DEPTH_FAR);
            }
        }
    }

    /**
     * The darkened world of a closed screen at {@code opacity} (1 = as vanilla drew it): the menu blur (radius already
     * scaled for this frame, see {@link OpenBackdrop#blurRadius}), then the gradient or the menu texture's black.
     * The fill covers the whole window in normalised device coordinates, the colours at the corners as
     * {@code GuiGraphicsExtractor#fillGradient} puts them.
     */
    private static void drawBackdrop(int kind, float opacity) {
        if (opacity <= 0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if ((kind & OpenBackdrop.BLUR) != 0) {
            mc.gameRenderer.processBlurEffect();
        }
        int top;
        int bottom;
        if ((kind & OpenBackdrop.GRADIENT) != 0) {
            top = OpenBackdrop.withAlpha(OpenBackdrop.GRADIENT_TOP, opacity);
            bottom = OpenBackdrop.withAlpha(OpenBackdrop.GRADIENT_BOTTOM, opacity);
        } else if ((kind & OpenBackdrop.MENU) != 0) {
            top = OpenBackdrop.withAlpha(OpenBackdrop.MENU_COLOR, opacity);
            bottom = top;
        } else {
            return;
        }
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("shatter_gui on top");
        }
        if (screenBuffer == null) {
            screenBuffer = new ProjectionMatrixBuffer("shatter_gui backdrop");
        }
        RenderType type = ShardRenderTypes.backdrop();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(screenBuffer.getBuffer(new Matrix4f()), ProjectionType.ORTHOGRAPHIC);
            modelView.identity();
            ImmediateDraw.draw(type, new PoseStack().last(), (pose, builder) -> {
                builder.addVertex(-1f, 1f, 0f).setColor(top);
                builder.addVertex(-1f, -1f, 0f).setColor(bottom);
                builder.addVertex(1f, -1f, 0f).setColor(bottom);
                builder.addVertex(1f, 1f, 0f).setColor(top);
            });
        } catch (RuntimeException e) {
            ImmediateDraw.discard();
            if (!failed) {
                failed = true;
                ShatterGuiClient.LOGGER.warn("Drawing the fading background failed; it is skipped", e);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
