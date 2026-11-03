package net.shattergui.compat.spatial;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.shattergui.capture.CapturePool;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.mixin.InventoryRendererAccessor;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardManager;
import org.joml.Vector3f;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.render.InventoryRenderer;
import org.tastytrash.spatialGUI.render.SpatialGUIRenderer;
import org.tastytrash.spatialGUI.util.RenderUtil;

import java.util.Locale;

/**
 * The only class that links against Spatial GUI. Reached through {@link SpatialGuiCompat} only, which catches anything
 * thrown here. Uses public members that exist with the same signatures in 1.8 and 1.9, plus
 * {@link InventoryRendererAccessor} for the close-animation state.
 *
 * <p>Two steps: {@link #queue} when the screen closes (during the tick), {@link #complete} in the next frame after the
 * level is drawn. By then Spatial GUI has drawn its plane for that frame, so the plane and camera read there are
 * exactly what is on screen, and the texture copy runs inside the frame like every other GPU copy (a copy issued
 * during the tick stalled ~12 ms waiting for the GPU). Spatial GUI keeps drawing its first closing frame, and the shards
 * replace it from the next frame on.
 */
final class SpatialGuiBridge {
    /** Give up if the frame never comes; Spatial GUI then just plays its own animation. */
    private static final int MAX_WAIT_FRAMES = 5;

    private record Pending(Screen screen, ImageRect bounds, ShatterEffect effect, FlyTarget target, ParamValues params,
                           boolean preview) {
    }

    /** Far enough back that Spatial GUI's open animation (scale and fade, a few hundred ms) counts as finished. */
    private static final long OPEN_ANIMATION_SKIP_MS = 60_000;

    private static Pending pending;
    private static int waitedFrames;

    /** A screen that opens by gathering on the plane. */
    private record PendingOpen(Screen screen, ImageRect bounds, ShatterEffect effect, FlyTarget target,
                               ParamValues params) {
    }

    /**
     * The frame (after opening) whose texture the pieces are cut from. Spatial GUI prepares its target at the start of
     * a frame and may draw the very first one without the screen; the plane is hidden meanwhile, so waiting costs
     * nothing visible but a frame of empty world.
     */
    private static final int OPEN_CAPTURE_FRAME = 2;
    private static PendingOpen pendingOpen;
    private static int openFrames;

    private SpatialGuiBridge() {
    }

    static boolean isShowing(Screen screen) {
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        return renderer != null && SpatialGUIClient.isEnabled() && renderer.getHookedScreen() == screen;
    }

    /** Right after {@code Minecraft#setScreen(null)}. Returns false if Spatial GUI has nothing to shatter. */
    /**
     * Its open animation runs on {@code currentTimeMillis() - screenOpenTime}, set when it hooks the screen (Fabric
     * {@code ScreenEvents.BEFORE_INIT}, i.e. inside {@code setScreen}). Moving the open time back makes it finished.
     */
    static boolean cancelOpenAnimation() {
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        if (renderer == null) {
            return false;
        }
        renderer.getInventoryRenderer().setScreenOpenTime(System.currentTimeMillis() - OPEN_ANIMATION_SKIP_MS);
        return true;
    }

    static void cancelPending() {
        pending = null;
        pendingOpen = null;
    }

    /** The screen being opened closed or was replaced: its gathering is not started. */
    static void cancelPendingOpen() {
        pendingOpen = null;
    }

    /**
     * Right after {@code Minecraft#setScreen(screen)} (Spatial GUI has hooked it): its open animation is skipped (the
     * plane is at full size from the first frame), and in a frame soon after its texture is copied and the pieces are
     * laid on its plane ({@link #completeOpen}). Until they have landed the plane is drawn transparent
     * ({@code OpenAssemble.spatialAlpha}).
     */
    static boolean queueOpen(Screen screen, ShatterEffect effect, FlyTarget target, ParamValues params) {
        long start = ShatterProfiler.now();
        if (!isShowing(screen) || !cancelOpenAnimation()) {
            return false;
        }
        ImageRect content = ScreenCapture.measureContentBounds(screen);
        pendingOpen = new PendingOpen(screen, content, effect, target, params);
        openFrames = 0;
        ShatterProfiler.openStep("spatial.queueOpen (" + ScreenCapture.describe(content, ScreenCapture.guiBounds(screen))
                + ")", start);
        return true;
    }

    /**
     * After the GUI is drawn in a frame: by then Spatial GUI has drawn this frame's screen into its texture (in its own
     * hook before {@code GuiRenderer#render}). Copies it and spawns the gathering wave on the plane as it is now.
     */
    static boolean completeOpen() {
        PendingOpen job = pendingOpen;
        if (job == null) {
            return false;
        }
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        if (renderer == null || renderer.getHookedScreen() != job.screen()) {
            pendingOpen = null;
            ShatterProfiler.openValue("spatial.openSkipped", "screen no longer on the plane");
            return false;
        }
        if (++openFrames < OPEN_CAPTURE_FRAME) {
            return false;
        }
        InventoryRenderer inventory = renderer.getInventoryRenderer();
        inventory.updateQuadBasis();
        RenderUtil.QuadBasis basis = inventory.getQuadBasis();
        TextureTarget target = renderer.getTargetManager().getInventoryTarget();
        GpuTexture color = target != null ? target.getColorTexture() : null;
        if (basis == null || color == null || basis.halfWidth() <= 0 || basis.halfHeight() <= 0) {
            if (openFrames > MAX_WAIT_FRAMES) {
                pendingOpen = null;
                ShatterProfiler.openValue("spatial.openSkipped", "no plane or texture after " + openFrames + " frames");
            }
            return false;
        }
        pendingOpen = null;
        long start = ShatterProfiler.now();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        SpatialPlane plane = new SpatialPlane(camera.position(), basis.centerOffset(), basis.right(), basis.up(),
                basis.normal(), basis.halfWidth(), basis.halfHeight(), FabricLoader.getInstance().isModLoaded("vulkanmod"));
        CapturePool.Slot slot = ShardManager.acquireSlot(target.width, target.height, color.getFormat());
        if (slot == null) {
            return false;
        }
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(color, slot.getTexture(), 0, 0, 0, 0, 0, target.width, target.height);
        ShardManager.spawn(slot, plane, job.bounds(), new Vector3f(camera.forwardVector()), job.effect(), job.target(),
                job.params(), false, camera.position(), job.screen());
        ShatterProfiler.openStep(String.format(Locale.ROOT,
                "spatial.completeOpen (frame +%d, center %.2f %.2f %.2f, size %.2fx%.2f, %dx%d)", openFrames,
                plane.center().x, plane.center().y, plane.center().z, basis.halfWidth() * 2, basis.halfHeight() * 2,
                target.width, target.height), start);
        return true;
    }

    static boolean queue(Screen screen, ShatterEffect effect, FlyTarget flyTarget, ParamValues params, boolean preview) {
        long start = ShatterProfiler.now();
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        if (renderer == null) {
            return false;
        }
        if (!((Object) renderer.getInventoryRenderer() instanceof InventoryRendererAccessor)) {
            throw new IllegalStateException("InventoryRenderer accessor mixin was not applied");
        }
        TextureTarget target = renderer.getTargetManager().getInventoryTarget();
        if (target == null || target.getColorTexture() == null) {
            ShatterProfiler.value("spatial.skipped", "no texture, using the flat capture");
            return false;
        }
        long measure = ShatterProfiler.now();
        ImageRect content = ScreenCapture.measureContentBounds(screen);
        ShatterProfiler.step("spatial.contentBounds (" + ScreenCapture.describe(content, ScreenCapture.guiBounds(screen))
                + ")", measure);
        pending = new Pending(screen, content, effect, flyTarget, params, preview);
        waitedFrames = 0;
        ShatterProfiler.step("spatial.queue (tick)", start);
        return true;
    }

    /** In the frame after the close, after the level (and Spatial GUI's plane) is drawn. */
    static void complete() {
        Pending job = pending;
        if (job == null) {
            return;
        }
        if (++waitedFrames > MAX_WAIT_FRAMES) {
            pending = null;
            giveUp(job, "cancelled after " + waitedFrames + " frames");
            return;
        }
        pending = null;
        long start = ShatterProfiler.now();
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        if (renderer == null) {
            giveUp(job, "Spatial GUI renderer gone");
            return;
        }
        InventoryRenderer inventory = renderer.getInventoryRenderer();
        boolean closing = inventory.isClosing();
        boolean firstPerson = closing && inventory.isClosingFirstPerson();
        ClosingBasis closingBasis = readClosingBasis(inventory, closing, firstPerson);
        RenderUtil.QuadBasis basis = closingBasis.quad();
        TextureTarget target = renderer.getTargetManager().getInventoryTarget();
        GpuTexture color = target != null ? target.getColorTexture() : null;
        if (basis == null || color == null || basis.halfWidth() <= 0 || basis.halfHeight() <= 0) {
            giveUp(job, "plane or texture gone before the frame");
            return;
        }

        // The plane is relative to this frame's camera; freeze it in world space.
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean curved = closingBasis.curved();
        SpatialPlane plane = new SpatialPlane(camera.position(), basis.centerOffset(), basis.right(), basis.up(),
                basis.normal(), basis.halfWidth(), basis.halfHeight(), FabricLoader.getInstance().isModLoaded("vulkanmod"));
        ShatterProfiler.step(String.format(Locale.ROOT,
                "spatial.readPlane (frame +%d, %s, %s, closeAnimation=%s, center %.2f %.2f %.2f, size %.2fx%.2f)",
                ShatterProfiler.frameIndex(), closing ? (firstPerson ? "first person" : "third person") : "mode n/a",
                curved ? "curved -> flat fallback" : "flat", closing,
                plane.center().x, plane.center().y, plane.center().z, basis.halfWidth() * 2, basis.halfHeight() * 2), start);

        long step = ShatterProfiler.now();
        CapturePool.Slot slot = ShardManager.acquireSlot(target.width, target.height, color.getFormat());
        if (slot == null) {
            giveUp(job, "no texture slot");
            return;
        }
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(color, slot.getTexture(), 0, 0, 0, 0, 0, target.width, target.height);
        ShatterProfiler.step("spatial.copyTexture (GPU, in frame, " + target.width + "x" + target.height + ")", step);

        step = ShatterProfiler.now();
        hideClosingPlane();
        ShatterProfiler.step("spatial.cancelCloseAnimation", step);

        ShardManager.spawn(slot, plane, job.bounds(), new Vector3f(camera.forwardVector()), job.effect(), job.target(),
                job.params(), job.preview());
        ShatterProfiler.step("spatial.complete total", start);
    }

    /**
     * Spatial GUI's plane of the screen that just closed is never drawn again: its close animation is cancelled. From
     * the next frame on the plane is not drawn; closing stays true, so the third-person camera still eases back and
     * Spatial GUI cleans up through its own finishClosing(); first person has no camera move to wait for. The one place
     * that hides it: every way a close takes the plane over ends here, also when it falls back to the flat copy.
     */
    static void hideClosingPlane() {
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        if (renderer == null) {
            return;
        }
        InventoryRenderer inventory = renderer.getInventoryRenderer();
        if (inventory.isClosing()) {
            InventoryRendererAccessor animation = (InventoryRendererAccessor) (Object) inventory;
            animation.shatterGui$setCloseStartElapsed(0L);
            if (inventory.isClosingFirstPerson()) {
                animation.shatterGui$setCloseDurationMs(0L);
            }
        }
    }

    /** The plane could not be taken: it is hidden anyway and the effect plays on a flat copy of the closed screen. */
    private static void giveUp(Pending job, String why) {
        hideClosingPlane();
        ShatterProfiler.value("spatial.fallback to the flat copy", why);
        ScreenCapture.requestCapture(job.screen(), job.effect(), job.target(), job.params(), job.preview());
    }

    private record ClosingBasis(RenderUtil.QuadBasis quad, boolean curved) {
    }

    /**
     * The plane as Spatial GUI draws it while closing. {@code getQuadBasis()} recomputes with
     * {@code SpatialGUIClient.getEffectiveFirstPersonMode()}, which its camera resets to false as soon as the screen is
     * gone, while drawing uses the mode the screen was closed in. So the mode is set to that for the computation and then
     * put back, recomputing again so Spatial GUI's own cached basis matches its state.
     */
    private static ClosingBasis readClosingBasis(InventoryRenderer inventory, boolean closing, boolean firstPerson) {
        if (!closing) {
            return new ClosingBasis(inventory.getQuadBasis(), inventory.getCylinderBasis() != null);
        }
        boolean saved = SpatialGUIClient.getEffectiveFirstPersonMode();
        try {
            SpatialGUIClient.setEffectiveFirstPersonMode(firstPerson);
            inventory.updateQuadBasis();
            return new ClosingBasis(inventory.getQuadBasis(), inventory.getCylinderBasis() != null);
        } finally {
            SpatialGUIClient.setEffectiveFirstPersonMode(saved);
            inventory.updateQuadBasis();
        }
    }
}
