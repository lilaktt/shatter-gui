package net.shattergui.capture;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.shattergui.ShatterGuiClient;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.mixin.AbstractContainerScreenAccessor;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.CameraFov;
import net.shattergui.shatter.CameraPlane;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardManager;
import org.joml.Vector3f;

import java.util.Locale;

public final class ScreenCapture {
    private static final int NO_MOUSE = -100_000;
    private static final int MAX_WAIT_FRAMES = 5;
    private static final int CONTENT_MARGIN = 2;

    public record CameraSnapshot(double x, double y, double z, Vector3f forward, Vector3f up, Vector3f left,
                                 float fovDegrees, int windowWidth, int windowHeight) {
    }

    private record Capture(CameraSnapshot camera, ShatterEffect effect, ImageRect bounds, FlyTarget target,
                           ParamValues params, boolean preview, Screen screen) {
    }

    private static Screen pendingScreen;
    private static Capture pending;
    private static int waitedFrames;

    private static boolean extracting;
    private static final float[] DRAWN = new float[4];

    private ScreenCapture() {
    }

    public static void requestCapture(Screen screen, ShatterEffect effect, FlyTarget target) {
        requestCapture(screen, effect, target, ParamValues.current(), false);
    }

    public static void requestCapture(Screen screen, ShatterEffect effect, FlyTarget target, ParamValues params,
                                      boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        RenderTarget main = mc.getMainRenderTarget();
        pendingScreen = screen;
        pending = new Capture(new CameraSnapshot(
                camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
                new Vector3f(camera.getLookVector()), new Vector3f(camera.getUpVector()), new Vector3f(camera.getLeftVector()),
                CameraFov.of(camera), main.width, main.height), effect, guiBounds(screen), target, params, preview, screen);
        waitedFrames = 0;
    }

    public static void cancelPendingOpening() {
        if (pending != null && pending.effect().opens()) {
            clearPending();
        }
    }

    public static void cancelPending() {
        clearPending();
    }

    public static boolean isExtracting() {
        return extracting;
    }

    public static void captureBeforeHud(float partialTick) {
        if (pendingScreen == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (++waitedFrames > MAX_WAIT_FRAMES || pending.camera().windowWidth() != main.width
                || pending.camera().windowHeight() != main.height) {
            ShatterProfiler.value("capture.cancelled (frames waited)", waitedFrames);
            clearPending();
            return;
        }
        long start = ShatterProfiler.now();
        Screen screen = pendingScreen;
        Capture capture = pending;
        clearPending();

        CapturePool.Slot slot = ShardManager.acquireSlot(main.width, main.height);
        if (slot == null) {
            return;
        }
        TextureTarget target = slot.target();
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        DRAWN[0] = Float.POSITIVE_INFINITY;
        DRAWN[1] = Float.POSITIVE_INFINITY;
        DRAWN[2] = Float.NEGATIVE_INFINITY;
        DRAWN[3] = Float.NEGATIVE_INFINITY;
        boolean drawn = true;
        extracting = true;
        RenderSystem.defaultBlendFunc();
        try {
            screen.renderWithTooltip(graphics, NO_MOUSE, NO_MOUSE, partialTick);
            graphics.flush();
        } catch (Exception e) {
            ShatterGuiClient.LOGGER.warn("Could not draw closing screen {} again", screen.getClass().getName(), e);
            drawn = false;
        } finally {
            extracting = false;
            RenderSystem.defaultBlendFunc();
            main.bindWrite(true);
        }
        ShatterProfiler.step("capture.drawScreenIntoSlot (frame +" + ShatterProfiler.frameIndex() + ", GPU only)", start);
        if (!drawn) {
            CapturePool.release(slot);
            return;
        }
        long measure = ShatterProfiler.now();
        ImageRect content = contentBounds(screen);
        ShatterProfiler.step("capture.contentBounds (" + describe(content, capture.bounds()) + ")", measure);
        ShardManager.spawn(slot, new CameraPlane(capture.camera()), content, capture.camera().forward(),
                capture.effect(), capture.target(), capture.params(), capture.preview(), null, capture.screen());
    }

    public static void drawnAt(float x, float y) {
        if (x < DRAWN[0]) {
            DRAWN[0] = x;
        }
        if (y < DRAWN[1]) {
            DRAWN[1] = y;
        }
        if (x > DRAWN[2]) {
            DRAWN[2] = x;
        }
        if (y > DRAWN[3]) {
            DRAWN[3] = y;
        }
    }

    public static boolean recordsVertices() {
        return extracting && RenderSystem.isOnRenderThread();
    }

    public static ImageRect guiBounds(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container && screen.width > 0 && screen.height > 0) {
            AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) container;
            double left = panel.shatterGui$leftPos();
            double top = panel.shatterGui$topPos();
            return ImageRect.of(left / screen.width, top / screen.height,
                    (left + panel.shatterGui$imageWidth()) / screen.width,
                    (top + panel.shatterGui$imageHeight()) / screen.height);
        }
        return ImageRect.FULL;
    }

    private static ImageRect contentBounds(Screen screen) {
        ImageRect panel = guiBounds(screen);
        if (!(DRAWN[0] < DRAWN[2] && DRAWN[1] < DRAWN[3]) || screen.width <= 0 || screen.height <= 0) {
            return panel;
        }
        double w = screen.width;
        double h = screen.height;
        double x0 = Math.max(0, (DRAWN[0] - CONTENT_MARGIN) / w);
        double y0 = Math.max(0, (DRAWN[1] - CONTENT_MARGIN) / h);
        double x1 = Math.min(1, (DRAWN[2] + CONTENT_MARGIN) / w);
        double y1 = Math.min(1, (DRAWN[3] + CONTENT_MARGIN) / h);
        if (screen instanceof AbstractContainerScreen<?>) {
            x0 = Math.min(x0, panel.x0());
            y0 = Math.min(y0, panel.y0());
            x1 = Math.max(x1, panel.x1());
            y1 = Math.max(y1, panel.y1());
        }
        if (x0 >= x1 || y0 >= y1) {
            return panel;
        }
        return ImageRect.of(x0, y0, x1, y1);
    }

    public static boolean busy() {
        return pendingScreen != null;
    }

    public static String describe(ImageRect content, ImageRect panel) {
        return String.format(Locale.ROOT, "%.0f%% x %.0f%%, panel %.0f%% x %.0f%%", content.width() * 100,
                content.height() * 100, panel.width() * 100, panel.height() * 100);
    }

    private static void clearPending() {
        pendingScreen = null;
        pending = null;
    }
}
