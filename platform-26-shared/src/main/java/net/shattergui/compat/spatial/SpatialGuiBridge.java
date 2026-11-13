package net.shattergui.compat.spatial;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.capture.CapturePool;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.mixin.SpatialPlaneAccessor;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardManager;
import org.joml.Vector3f;

import java.util.Locale;

final class SpatialGuiBridge {
    private static final int MAX_WAIT_FRAMES = 5;

    private record Pending(Screen screen, ImageRect bounds, ShatterEffect effect, FlyTarget target, ParamValues params,
                           boolean preview) {
    }

    private static final long OPEN_ANIMATION_SKIP_MS = 60_000;

    private static Pending pending;
    private static int waitedFrames;
    private static ShardManager.Batch spawned;

    private record PendingOpen(Screen screen, ImageRect bounds, ShatterEffect effect, FlyTarget target,
                               ParamValues params) {
    }

    private static final int OPEN_CAPTURE_FRAME = 2;
    private static PendingOpen pendingOpen;
    private static int openFrames;

    private SpatialGuiBridge() {
    }

    private static SpatialHandles api() {
        return SpatialHandles.get();
    }

    static boolean isShowing(Screen screen) {
        SpatialHandles api = api();
        Object renderer = api.renderer();
        return renderer != null && api.enabled() && api.hookedScreen(renderer) == screen;
    }

    static boolean cancelOpenAnimation() {
        SpatialHandles api = api();
        Object renderer = api.renderer();
        if (renderer == null) {
            return false;
        }
        api.setScreenOpenTime(api.plane(renderer), System.currentTimeMillis() - OPEN_ANIMATION_SKIP_MS);
        return true;
    }

    static void cancelPending() {
        pending = null;
        pendingOpen = null;
    }

    static void cancelPendingOpen() {
        pendingOpen = null;
    }

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

    static boolean completeOpen() {
        PendingOpen job = pendingOpen;
        if (job == null) {
            return false;
        }
        SpatialHandles api = api();
        Object renderer = api.renderer();
        if (renderer == null || api.hookedScreen(renderer) != job.screen()) {
            pendingOpen = null;
            ShatterProfiler.openValue("spatial.openSkipped", "screen no longer on the plane");
            return false;
        }
        if (++openFrames < OPEN_CAPTURE_FRAME) {
            return false;
        }
        Object inventory = api.plane(renderer);
        api.updateQuadBasis(inventory);
        SpatialHandles.Quad basis = api.quadBasis(inventory);
        TextureTarget target = api.texture(renderer);
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
        Camera camera = Mc26.camera();
        SpatialPlane plane = new SpatialPlane(camera.position(), basis.centerOffset(), basis.right(), basis.up(),
                basis.normal(), basis.halfWidth(), basis.halfHeight(), FabricLoader.getInstance().isModLoaded("vulkanmod"));
        CapturePool.Slot slot = ShardManager.acquireSlot(target.width, target.height, color.getFormat());
        if (slot == null) {
            return false;
        }
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(color, slot.getTexture(), 0, 0, 0, 0, 0, target.width, target.height);
        net.shattergui.shatter.PlaneBasis captured = basis(basis, camera.position());
        ShardManager.spawn(slot, plane, job.bounds(), new Vector3f(camera.forwardVector()), job.effect(), job.target(),
                job.params(), false, new ShardManager.OpenAnchor(captured, SpatialGuiBridge::planeNow, true), job.screen());
        ShatterProfiler.openStep(String.format(Locale.ROOT,
                "spatial.completeOpen (frame +%d, center %.2f %.2f %.2f, size %.2fx%.2f, %dx%d)", openFrames,
                plane.center().x, plane.center().y, plane.center().z, basis.halfWidth() * 2, basis.halfHeight() * 2,
                target.width, target.height), start);
        return true;
    }

    private static net.shattergui.shatter.PlaneBasis basis(SpatialHandles.Quad quad, net.minecraft.world.phys.Vec3 origin) {
        Vector3f c = quad.centerOffset();
        Vector3f forward = new Vector3f(quad.normal()).normalize();
        if (forward.dot(c) < 0) {
            forward.negate();
        }
        return new net.shattergui.shatter.PlaneBasis(new org.joml.Vector3d(origin.x + c.x(), origin.y + c.y(), origin.z + c.z()),
                new Vector3f(quad.right()).normalize(), new Vector3f(quad.up()).normalize(), forward, quad.halfWidth(),
                quad.halfHeight());
    }

    static net.shattergui.shatter.PlaneBasis planeNow() {
        try {
            SpatialHandles api = api();
            Object renderer = api.renderer();
            if (renderer == null || api.hookedScreen(renderer) == null) {
                return null;
            }
            SpatialHandles.Quad quad = api.quadBasis(api.plane(renderer));
            if (quad == null || quad.halfWidth() <= 0 || quad.halfHeight() <= 0) {
                return null;
            }
            return basis(quad, net.minecraft.world.phys.Vec3.ZERO);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean queue(Screen screen, ShatterEffect effect, FlyTarget flyTarget, ParamValues params, boolean preview) {
        long start = ShatterProfiler.now();
        SpatialHandles api = api();
        Object renderer = api.renderer();
        if (renderer == null) {
            return false;
        }
        if (!(api.plane(renderer) instanceof SpatialPlaneAccessor)) {
            throw new IllegalStateException("the Spatial GUI plane accessor mixin was not applied");
        }
        TextureTarget target = api.texture(renderer);
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
        SpatialHandles api = api();
        Object renderer = api.renderer();
        if (renderer == null) {
            giveUp(job, "Spatial GUI renderer gone");
            return;
        }
        Object inventory = api.plane(renderer);
        boolean closing = api.closing(inventory);
        boolean firstPerson = closing && api.closingFirstPerson(inventory);
        ClosingBasis closingBasis = readClosingBasis(api, inventory, closing, firstPerson);
        SpatialHandles.Quad basis = closingBasis.quad();
        TextureTarget target = api.texture(renderer);
        GpuTexture color = target != null ? target.getColorTexture() : null;
        if (basis == null || color == null || basis.halfWidth() <= 0 || basis.halfHeight() <= 0) {
            giveUp(job, "plane or texture gone before the frame");
            return;
        }

        Camera camera = Mc26.camera();
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
        spawned = ShardManager.lastSpawned();
        ShatterProfiler.step("spatial.complete total", start);
    }

    static ShardManager.Batch takeSpawned() {
        ShardManager.Batch batch = spawned;
        spawned = null;
        return batch;
    }

    static void hideClosingPlane() {
        SpatialHandles api = api();
        Object renderer = api.renderer();
        if (renderer == null) {
            return;
        }
        Object inventory = api.plane(renderer);
        if (api.closing(inventory)) {
            SpatialPlaneAccessor animation = (SpatialPlaneAccessor) inventory;
            animation.shatterGui$setCloseStartElapsed(0L);
            if (api.closingFirstPerson(inventory)) {
                animation.shatterGui$setCloseDurationMs(0L);
            }
        }
    }

    private static void giveUp(Pending job, String why) {
        hideClosingPlane();
        ShatterProfiler.value("spatial.fallback to the flat copy", why);
        ScreenCapture.requestCapture(job.screen(), job.effect(), job.target(), job.params(), job.preview());
    }

    private record ClosingBasis(SpatialHandles.Quad quad, boolean curved) {
    }

    private static ClosingBasis readClosingBasis(SpatialHandles api, Object inventory, boolean closing, boolean firstPerson) {
        if (!closing) {
            return new ClosingBasis(api.quadBasis(inventory), api.curved(inventory));
        }
        boolean saved = api.firstPersonMode();
        try {
            api.firstPersonMode(firstPerson);
            api.updateQuadBasis(inventory);
            return new ClosingBasis(api.quadBasis(inventory), api.curved(inventory));
        } finally {
            api.firstPersonMode(saved);
            api.updateQuadBasis(inventory);
        }
    }

    static Screen hookedScreenForTests() {
        Object renderer = api().renderer();
        return renderer == null ? null : api().hookedScreen(renderer);
    }

    static boolean planeClosingForTests() {
        Object renderer = api().renderer();
        return renderer != null && api().closing(api().plane(renderer));
    }

    static long closeStartElapsedForTests() {
        Object renderer = api().renderer();
        if (renderer == null) {
            return 0L;
        }
        Object plane = api().plane(renderer);
        return api().closing(plane) ? ((SpatialPlaneAccessor) plane).shatterGui$getCloseStartElapsed() : 0L;
    }
}
