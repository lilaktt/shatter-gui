package net.shattergui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.debug.CloseWatchdog;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.ui.preview.RealPreview;

/** What the mod does at the points of a frame; the version's {@code GameRendererMixin} calls these. */
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

    /**
     * After the level and the hand, before vanilla clears the depth for the GUI: the fading darkness of a closed screen
     * and the effects over the world, depth-tested against the hand (see {@link OnTopPass#drawAfterLevel}).
     */
    public static void afterLevel(GpuBufferSlice noFog) {
        long start = System.nanoTime();
        OnTopPass.drawAfterLevel(noFog);
        CloseWatchdog.step("draw the effects over the world", start);
    }

    /** At the start of the GUI's extraction. True: this frame's GUI is the capture or the preview, nothing else. */
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
        // The effects over the world are drawn already (after the level), so the backed-up frame has them.
        ScreenCapture.beforeGuiRender();
        CloseWatchdog.step("capture: back up and clear the frame", start);
        RealPreview.beforeGuiRender();
    }

    public static void afterGuiRender(GpuBufferSlice noFog) {
        long start = System.nanoTime();
        ScreenCapture.afterGuiRender();
        start = CloseWatchdog.step("capture: copy to the texture pool and spawn the pieces", start);
        // The frame of the capture shows the world without the closed screen: its pieces are drawn in it right away,
        // where it was (else this one frame has neither the screen nor the pieces).
        ShardRenderer.drawFirstFrame(ScreenCapture.takeSpawnedClose(), noFog);
        CloseWatchdog.step("draw the first frame of the close", start);
        RealPreview.afterGuiRender();
        SpatialGuiCompat.completeOpenPending();
        // Openings over the GUI drawn so far (the HUD and the screen's own darkened background).
        ShardRenderer.drawOpenings(noFog);
    }
}
