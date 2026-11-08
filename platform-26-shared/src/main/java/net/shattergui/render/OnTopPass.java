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

public final class OnTopPass {
    public record Entry(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
    }

    private static final List<Entry> PENDING = new ArrayList<>();
    private static final List<Entry> SETTLED = new ArrayList<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f CLEAN_PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static final Matrix4f CLEAN_VIEW_ROTATION = new Matrix4f();
    private static ProjectionMatrixBuffer projectionBuffer;
    private static ProjectionMatrixBuffer screenBuffer;
    private static boolean failed;

    private OnTopPass() {
    }

    public static void levelProjection(Matrix4fc projection) {
        PROJECTION.set(projection);
    }

    public static void cleanProjection(Matrix4fc projection) {
        CLEAN_PROJECTION.set(projection);
    }

    public static void levelViewRotation(Matrix4fc viewRotation) {
        VIEW_ROTATION.set(viewRotation);
    }

    public static void cleanViewRotation(Matrix4fc viewRotation) {
        CLEAN_VIEW_ROTATION.set(viewRotation);
        VIEW_ROTATION.set(viewRotation);
    }

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

    public static boolean hasPending() {
        return !PENDING.isEmpty() || !SETTLED.isEmpty();
    }

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

    public static void drawAfterLevel(GpuBufferSlice noFog) {
        int closing = OpenBackdrop.closingKind();
        if (!SETTLED.isEmpty()) {
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
            draw(PENDING, PROJECTION, VIEW_ROTATION, IrisCompat.shaderPackInUse(), true, noFog);
        } finally {
            PENDING.clear();
        }
    }

    public static void drawOpenings(List<Entry> entries, boolean flat, GpuBufferSlice noFog) {
        draw(entries, flat ? CLEAN_PROJECTION : PROJECTION, flat ? CLEAN_VIEW_ROTATION : VIEW_ROTATION, true, true, noFog);
    }

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
                encoder.clearDepthTexture(main.getDepthTexture(), Mc26.DEPTH_FAR);
            }
        }
    }

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
