package net.shattergui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.debug.CloseWatchdog;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.ui.preview.RealPreview;

public final class FrameHooks {
    private FrameHooks() {
    }

    public static void frameStart() {
        ShatterProfiler.frameStart();
    }

    public static void frameEnd() {
        ShatterProfiler.frameEnd();
        CloseWatchdog.frameEnd();
        net.shattergui.debug.FrameAudit.endFrame();
        ImmediateDraw.endFrame();
    }

    public static void beforeHand(GpuBufferSlice noFog, org.joml.Matrix4fc viewRotation) {
        long start = System.nanoTime();
        OnTopPass.levelViewRotation(viewRotation);
        OnTopPass.drawSettled(noFog);
        CloseWatchdog.step("draw the settled pieces in the world", start);
    }

    public static void afterLevel(GpuBufferSlice noFog, org.joml.Matrix4fc viewRotation) {
        long start = System.nanoTime();
        OnTopPass.levelViewRotation(viewRotation);
        OnTopPass.drawAfterLevel(noFog);
        CloseWatchdog.step("draw the effects over the world", start);
    }

    public static boolean extractGui(GuiRenderState state, float partialTicks) {
        long start = System.nanoTime();
        boolean captured = ScreenCapture.extractPendingScreen(state, partialTicks);
        CloseWatchdog.step("capture: draw the closed screen", start);
        return captured || net.shattergui.ui.preview.RealPreview.extract(state, partialTicks);
    }

    public static void beforeGuiRender() {
        long start = System.nanoTime();
        SpatialGuiCompat.completePending();
        start = CloseWatchdog.step("Spatial GUI: copy its plane and spawn the pieces", start);
        ScreenCapture.beforeGuiRender();
        CloseWatchdog.step("capture: back up and clear the frame", start);
        RealPreview.beforeGuiRender();
    }

    public static void afterGuiRender(GpuBufferSlice noFog) {
        long start = System.nanoTime();
        ScreenCapture.afterGuiRender();
        start = CloseWatchdog.step("capture: copy to the texture pool and spawn the pieces", start);
        ShardRenderer.drawFirstFrame(ScreenCapture.takeSpawnedClose(), noFog);
        CloseWatchdog.step("draw the first frame of the close", start);
        RealPreview.afterGuiRender();
        SpatialGuiCompat.completeOpenPending();
        ShardRenderer.drawOpenings(noFog);
    }
}
