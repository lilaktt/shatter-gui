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
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.CameraPlane;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardManager;
import net.shattergui.ShatterGuiClient;
import org.joml.Vector3f;

import java.util.function.Consumer;
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
    private static Capture armed;
    private static GpuTexture backup;
    private static long guiRenderStart;
    private static ShardManager.Batch spawnedClose;

    private ScreenCapture() {
    }

    public static void requestCapture(Screen screen, ShatterEffect effect, FlyTarget target) {
        requestCapture(screen, effect, target, ParamValues.current(), false);
    }

    public static void requestCapture(Screen screen, ShatterEffect effect, FlyTarget target, ParamValues params,
                                      boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = Mc26.camera();
        RenderTarget main = Mc26.mainTarget();
        pendingScreen = screen;
        pending = new Capture(new CameraSnapshot(
                camera.position().x, camera.position().y, camera.position().z,
                new Vector3f(camera.forwardVector()), new Vector3f(camera.upVector()), new Vector3f(camera.leftVector()),
                camera.getFov(), main.width, main.height), effect, guiBounds(screen), target, params, preview, screen);
        waitedFrames = 0;
    }

    public static void cancelPendingOpening() {
        if (pending != null && pending.effect().opens()) {
            clearPending();
        }
        if (armed != null && armed.effect().opens()) {
            armed = null;
        }
    }

    public static void cancelPending() {
        clearPending();
    }

    public static boolean isExtracting() {
        return extracting;
    }

    public static boolean extractPendingScreen(GuiRenderState state, float partialTick) {
        if (pendingScreen == null) {
            return false;
        }
        RenderTarget main = Mc26.mainTarget();
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

    public static void beforeGuiRender() {
        if (armed == null) {
            return;
        }
        long start = ShatterProfiler.now();
        RenderTarget main = Mc26.mainTarget();
        GpuTexture color = main.getColorTexture();
        if (color == null) {
            armed = null;
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        backup = CapturePool.backup(main.width, main.height, color.getFormat());
        encoder.copyTextureToTexture(color, backup, 0, 0, 0, 0, 0, main.width, main.height);
        Mc26.clearToTransparent(encoder, color);
        ShatterProfiler.step("capture.backupAndClear", start);
        guiRenderStart = ShatterProfiler.now();
    }

    public static void afterGuiRender() {
        Capture capture = armed;
        armed = null;
        if (capture == null || backup == null) {
            return;
        }
        ShatterProfiler.step("capture.guiRender (closing screen drawn to cleared target)", guiRenderStart);
        long start = ShatterProfiler.now();
        RenderTarget main = Mc26.mainTarget();
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
                    capture.effect(), capture.target(), capture.params(), capture.preview(),
                    new ShardManager.OpenAnchor(net.shattergui.shatter.PlaneBasis.flat(capture.camera()),
                            net.shattergui.shatter.PlaneBasis::flatNow, false),
                    capture.screen());
            ShardManager.Batch batch = ShardManager.lastSpawned();
            spawnedClose = batch != null && !capture.effect().opens() ? batch : null;
        }
    }

    public static ShardManager.Batch takeSpawnedClose() {
        ShardManager.Batch batch = spawnedClose;
        spawnedClose = null;
        return batch;
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

    public static boolean busy() {
        return pendingScreen != null || armed != null;
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
