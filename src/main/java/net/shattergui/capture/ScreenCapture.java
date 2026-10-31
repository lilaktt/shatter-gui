package net.shattergui.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.FlyTarget;
import net.shattergui.mixin.AbstractContainerScreenAccessor;
import net.shattergui.params.ParamValues;
import net.shattergui.shatter.CameraPlane;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardManager;
import net.shattergui.ShatterGuiClient;
import org.joml.Vector3f;

import java.util.function.Consumer;
import java.util.Locale;

/**
 * Captures the last frame of a closing screen as a transparent GPU texture. Nothing is read back to the CPU.
 *
 * <p>The screen is already gone when {@code Minecraft#setScreen} runs, so the capture happens on the next frame:
 * <ol>
 *   <li>{@link #requestCapture} remembers the old screen, the camera and the effect.</li>
 *   <li>In {@code GameRenderer#extractGui} the old screen is extracted instead of the HUD
 *       ({@link #extractPendingScreen}), with its blurred/darkened background suppressed, and its bounds are taken
 *       from the screen geometry.</li>
 *   <li>Around {@code GuiRenderer#render} the main target is backed up and cleared to transparent
 *       ({@link #beforeGuiRender}); after the GUI is drawn it is copied into a {@link CapturePool} texture and the world
 *       image is restored ({@link #afterGuiRender}). All of this is GPU-side copies.</li>
 * </ol>
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
    private static Capture armed;
    private static GpuTexture backup;
    private static long guiRenderStart;

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
                camera.position().x, camera.position().y, camera.position().z,
                new Vector3f(camera.forwardVector()), new Vector3f(camera.upVector()), new Vector3f(camera.leftVector()),
                camera.getFov(), main.width, main.height), effect, guiBounds(screen), target, params, preview, screen);
        waitedFrames = 0;
    }

    /** An opening's capture that has not happened yet is forgotten (its screen closed or was replaced). */
    public static void cancelPendingOpening() {
        if (pending != null && pending.effect().opens()) {
            clearPending();
        }
        if (armed != null && armed.effect().opens()) {
            armed = null;
        }
    }

    /** The mod was switched off: forget a capture that has not happened yet. */
    public static void cancelPending() {
        clearPending();
    }

    /** True while the closing screen is being extracted; used to skip its background. */
    public static boolean isExtracting() {
        return extracting;
    }

    /**
     * Called at the head of {@code GameRenderer#extractGui}. Returns true if it replaced this frame's GUI with the
     * closing screen (the caller then cancels the vanilla extraction).
     */
    public static boolean extractPendingScreen(GuiRenderState state, float partialTick) {
        if (pendingScreen == null) {
            return false;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (++waitedFrames > MAX_WAIT_FRAMES || pending.camera().windowWidth() != main.width
                || pending.camera().windowHeight() != main.height) {
            ShatterProfiler.value("capture.cancelled (frames waited)", waitedFrames);
            clearPending();
            return false;
        }
        long start = ShatterProfiler.now();
        Screen screen = pendingScreen;
        Capture capture = pending;
        clearPending();

        state.reset();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, NO_MOUSE, NO_MOUSE);
        extracting = true;
        try {
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, NO_MOUSE, NO_MOUSE, partialTick);
        } catch (Exception e) {
            ShatterGuiClient.LOGGER.warn("Could not re-extract closing screen {}", screen.getClass().getName(), e);
            state.reset();
            return true;
        } finally {
            extracting = false;
        }
        ShatterProfiler.step("capture.extractScreen (frame +" + ShatterProfiler.frameIndex() + ")", start);
        long measure = ShatterProfiler.now();
        ImageRect content = contentBounds(state, screen);
        armed = new Capture(capture.camera(), capture.effect(), content, capture.target(), capture.params(),
                capture.preview(), capture.screen());
        ShatterProfiler.step("capture.contentBounds (" + describe(content, capture.bounds()) + ")", measure);
        return true;
    }

    /** Before the GUI is drawn: back up the world image and clear the target to transparent. */
    public static void beforeGuiRender() {
        if (armed == null) {
            return;
        }
        long start = ShatterProfiler.now();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture color = main.getColorTexture();
        if (color == null) {
            armed = null;
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        backup = CapturePool.backup(main.width, main.height, color.getFormat());
        encoder.copyTextureToTexture(color, backup, 0, 0, 0, 0, 0, main.width, main.height);
        encoder.clearColorTexture(color, 0);
        ShatterProfiler.step("capture.backupAndClear", start);
        guiRenderStart = ShatterProfiler.now();
    }

    /** After the GUI is drawn: copy it into a pool texture (GPU-side), restore the world image, spawn shards. */
    public static void afterGuiRender() {
        Capture capture = armed;
        armed = null;
        if (capture == null || backup == null) {
            return;
        }
        ShatterProfiler.step("capture.guiRender (closing screen drawn to cleared target)", guiRenderStart);
        long start = ShatterProfiler.now();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture color = main.getColorTexture();
        int width = main.width;
        int height = main.height;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        CapturePool.Slot slot = ShardManager.acquireSlot(width, height, color.getFormat());
        if (slot != null) {
            encoder.copyTextureToTexture(color, slot.getTexture(), 0, 0, 0, 0, 0, width, height);
        }
        encoder.copyTextureToTexture(backup, color, 0, 0, 0, 0, 0, width, height);
        backup = null;
        ShatterProfiler.step("capture.copyToPoolAndRestore (GPU only)", start);

        if (slot != null) {
            ShardManager.spawn(slot, new CameraPlane(capture.camera()), capture.bounds(), capture.camera().forward(),
                    capture.effect(), capture.target(), capture.params(), capture.preview(), null, capture.screen());
        }
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
     * Where everything the screen draws is, as fractions of the screen: the union of the bounds of every element, item,
     * text and picture-in-picture in {@code state} (the screen was just extracted into it, without the shared
     * background), plus a small margin. Wider than {@link #guiBounds} for anything drawn outside the container panel:
     * creative tabs, scroll bars, the recipe book, panels of other mods. Falls back to {@link #guiBounds} if empty.
     */
    public static ImageRect contentBounds(GuiRenderState state, Screen screen) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        Consumer<ScreenArea> add = area -> {
            ScreenRectangle r = area.bounds();
            if (r != null && r.width() > 0 && r.height() > 0) {
                box[0] = Math.min(box[0], r.left());
                box[1] = Math.min(box[1], r.top());
                box[2] = Math.max(box[2], r.right());
                box[3] = Math.max(box[3], r.bottom());
            }
        };
        state.forEachElement(add::accept, GuiRenderState.TraverseRange.ALL);
        state.forEachItem(add::accept);
        state.forEachText(add::accept);
        state.forEachPictureInPicture(add::accept);
        if (box[0] >= box[2] || box[1] >= box[3] || screen.width <= 0 || screen.height <= 0) {
            return guiBounds(screen);
        }
        double w = screen.width;
        double h = screen.height;
        return ImageRect.of((box[0] - CONTENT_MARGIN) / w, (box[1] - CONTENT_MARGIN) / h,
                (box[2] + CONTENT_MARGIN) / w, (box[3] + CONTENT_MARGIN) / h);
    }

    /**
     * {@link #contentBounds} of a screen that is not being drawn this frame (Spatial GUI draws it into its own texture):
     * extracts it once more into a scratch state, on the CPU only.
     */
    public static ImageRect measureContentBounds(Screen screen) {
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, NO_MOUSE, NO_MOUSE);
        extracting = true;
        try {
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, NO_MOUSE, NO_MOUSE,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        } catch (Exception e) {
            ShatterGuiClient.LOGGER.warn("Could not measure closing screen {}", screen.getClass().getName(), e);
            return guiBounds(screen);
        } finally {
            extracting = false;
        }
        return contentBounds(state, screen);
    }

    /**
     * Extracts {@code screen} into {@code state} as a capture does: the mouse far away (nothing hovered, no tooltip) and
     * without the shared in-world background. For pictures of screens that are not on display (the editor's real
     * preview). The steps of {@code Screen#extractRenderStateWithTooltipAndSubtitles} are called one by one: that
     * method is also where Fabric's screen events run, and they need a screen opened through {@code setScreen}.
     */
    public static void extractQuietly(Screen screen, GuiRenderState state, float partialTick) {
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, NO_MOUSE, NO_MOUSE);
        extracting = true;
        try {
            graphics.nextStratum();
            screen.extractBackground(graphics, NO_MOUSE, NO_MOUSE, partialTick);
            graphics.nextStratum();
            screen.extractRenderState(graphics, NO_MOUSE, NO_MOUSE, partialTick);
            graphics.extractDeferredElements(NO_MOUSE, NO_MOUSE, partialTick);
        } finally {
            extracting = false;
        }
    }

    /** A close capture is waiting for its frame or running in this one (the real preview then waits). */
    public static boolean busy() {
        return pendingScreen != null || armed != null;
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
