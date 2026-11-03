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

/**
 * Captures the last frame of a closing screen as a transparent GPU texture. Nothing is read back to the CPU and
 * nothing is copied.
 *
 * <p>The screen is already gone when {@code Minecraft#setScreen} runs, so the capture happens on the next frame:
 * <ol>
 *   <li>{@link #requestCapture} remembers the old screen, the camera and the effect.</li>
 *   <li>In {@code GameRenderer#render}, after the level and right before the HUD ({@code Gui#render}), with the GUI
 *       projection already set up, {@link #captureBeforeHud} binds a {@link CapturePool} slot's render target,
 *       clears it to transparent and draws the old screen into it with its own {@code GuiGraphics} (its blurred or
 *       darkened background suppressed, the mouse far away), then binds the main target again and spawns the shards,
 *       which sample the slot's colour texture directly. The HUD and the rest of the frame are drawn as usual.</li>
 * </ol>
 * 1.21.1 draws the GUI at once into whatever framebuffer is bound, so unlike 26.1 (whose GUI renderer always draws into
 * the main target: back it up, clear, draw, copy, restore) the screen can be drawn straight into the slot.
 *
 * <p>Alpha: vanilla's {@code defaultBlendFunc} writes the source alpha over the destination's ({@code ONE, ZERO}), so a
 * partly transparent sprite drawn over the panel would punch a hole into the picture. While capturing,
 * {@code GlStateManagerMixin} makes every blend accumulate alpha ({@code ONE, ONE_MINUS_SRC_ALPHA}), as 26.1's GUI
 * pipelines do; the colours come out premultiplied, as there.
 */
public final class ScreenCapture {
    /** Far outside the screen so nothing is hovered and no tooltip is drawn. */
    private static final int NO_MOUSE = -100_000;
    /** Give up if the capture frame never happens (e.g. rendering is skipped). */
    private static final int MAX_WAIT_FRAMES = 5;
    /** GUI pixels added around the content bounds (outlines, shadows, rounding). */
    private static final int CONTENT_MARGIN = 2;

    public record CameraSnapshot(double x, double y, double z, Vector3f forward, Vector3f up, Vector3f left,
                                 float fovDegrees, int windowWidth, int windowHeight) {
    }

    /** {@code target}: where the fly effect flies to, or null. */
    /** {@code params}: the settings to run with; {@code preview}: started by the editor (runs while the mod is off). */
    private record Capture(CameraSnapshot camera, ShatterEffect effect, ImageRect bounds, FlyTarget target,
                           ParamValues params, boolean preview, Screen screen) {
    }

    private static Screen pendingScreen;
    private static Capture pending;
    private static int waitedFrames;

    private static boolean extracting;
    /** Bounds of every vertex drawn while capturing, in GUI pixels (see {@code BufferBuilderMixin}). */
    private static final float[] DRAWN = new float[4];

    private ScreenCapture() {
    }

    public static void requestCapture(Screen screen, ShatterEffect effect, FlyTarget target) {
        requestCapture(screen, effect, target, ParamValues.current(), false);
    }

    /** The editor's preview: given (unsaved) settings, and it runs even while the mod is switched off. */
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

    /** An opening's capture that has not happened yet is forgotten (its screen closed or was replaced). */
    public static void cancelPendingOpening() {
        if (pending != null && pending.effect().opens()) {
            clearPending();
        }
    }

    /** The mod was switched off: forget a capture that has not happened yet. */
    public static void cancelPending() {
        clearPending();
    }

    /** True while the closing screen is being drawn into its slot; used to skip its background. */
    public static boolean isExtracting() {
        return extracting;
    }

    /**
     * {@code GameRenderer#render}, right before {@code Gui#render}: the GUI projection and model-view are set up and
     * the main target is bound. Draws the pending screen into a pool slot and spawns its shards.
     */
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
        // clear() binds the target, clears colour and depth, and unbinds it.
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        DRAWN[0] = Float.POSITIVE_INFINITY;
        DRAWN[1] = Float.POSITIVE_INFINITY;
        DRAWN[2] = Float.NEGATIVE_INFINITY;
        DRAWN[3] = Float.NEGATIVE_INFINITY;
        boolean drawn = true;
        extracting = true;
        // The blend in force may be vanilla's default (alpha ONE, ZERO): start with the accumulating one.
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

    /** {@code BufferBuilderMixin}: a vertex the captured screen drew, in GUI pixels (render thread only). */
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

    /** True while the vertices drawn on this thread belong to the captured screen. */
    public static boolean recordsVertices() {
        return extracting && RenderSystem.isOnRenderThread();
    }

    /**
     * Where the GUI is inside a full-screen image of {@code screen}: the panel of a container screen
     * ({@code leftPos, topPos, imageWidth, imageHeight} over the screen size), or the whole image for any other screen.
     * Fractions, so this also holds for images rendered at another GUI scale or resolution.
     */
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

    /**
     * Where everything the screen drew is, as fractions of the screen: the bounds of every vertex it emitted (26.1 takes
     * the bounds of the elements of its GUI render state; 1.21.1 has none, so the vertices are watched while the screen
     * is drawn), plus a small margin, clipped to the screen. Wider than {@link #guiBounds} for anything drawn outside
     * the container panel: creative tabs, scroll bars, the recipe book, panels of other mods. A container's panel is
     * always inside (a renderer that bypasses the vertex calls cannot shrink it); without any vertex the panel / whole
     * screen.
     */
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

    /** A close capture is waiting for its frame (the real preview then waits). */
    public static boolean busy() {
        return pendingScreen != null;
    }

    /** For the profile log. */
    public static String describe(ImageRect content, ImageRect panel) {
        return String.format(Locale.ROOT, "%.0f%% x %.0f%%, panel %.0f%% x %.0f%%", content.width() * 100,
                content.height() * 100, panel.width() * 100, panel.height() * 100);
    }

    private static void clearPending() {
        pendingScreen = null;
        pending = null;
    }
}
