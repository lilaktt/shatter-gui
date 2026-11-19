package net.shattergui.fly;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.shattergui.mixin.AbstractContainerScreenAccessor;
import net.shattergui.shatter.EffectClock;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class FlyOpen {
    private static final float START_SCALE = 0.05f;
    private static final float MAX_NDC = 1.5f;
    private static final double MIN_DEPTH = 0.05;

    private static Screen screen;
    private static FlyTarget from;
    private static boolean spatial;
    private static boolean drawingFlat;
    private static int duration;
    private static double startTime = Double.NaN;
    private static long ticks;

    private FlyOpen() {
    }

    public static void start(Screen opened, FlyTarget origin, boolean onSpatialPlane, int durationTicks) {
        screen = opened;
        from = origin;
        spatial = onSpatialPlane;
        duration = Math.max(1, durationTicks);
        startTime = Double.NaN;
    }

    public static boolean running() {
        return screen != null;
    }

    public static void stop() {
        screen = null;
        from = null;
    }

    public static void tick() {
        ticks++;
    }

    public static boolean isAnimating(Screen s) {
        return s != null && s == screen && progress() < 1f;
    }

    public static boolean pushFlatTransform(Screen s, GuiGraphics graphics) {
        if (s != screen || spatial) {
            return false;
        }
        float t = progress();
        if (t >= 1f) {
            return false;
        }
        float e = easeOut(t);
        float centerX;
        float centerY;
        if (s instanceof AbstractContainerScreen<?> container) {
            AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) container;
            centerX = panel.shatterGui$leftPos() + panel.shatterGui$imageWidth() / 2f;
            centerY = panel.shatterGui$topPos() + panel.shatterGui$imageHeight() / 2f;
        } else {
            centerX = s.width / 2f;
            centerY = s.height / 2f;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (from.isEdge()) {
            from.edge().applyFlatEntry(pose, centerX, centerY, s.width, s.height, e);
        } else {
            float[] start = projectToGui(from.point(), s.width, s.height);
            pose.translate(Mth.lerp(e, start[0], centerX), Mth.lerp(e, start[1], centerY), 0f);
            float scale = Mth.lerp(e, START_SCALE, 1f);
            pose.scale(scale, scale, 1f);
            pose.translate(-centerX, -centerY, 0f);
        }
        drawingFlat = true;
        return true;
    }

    public static void popFlatTransform(GuiGraphics graphics) {
        graphics.pose().popPose();
        drawingFlat = false;
    }

    public static boolean hidesPictureInPicture() {
        return drawingFlat;
    }

    public static Matrix4f spatialMatrix(Vector3fc planeCenter) {
        if (screen == null || !spatial) {
            return null;
        }
        float t = progress();
        if (t >= 1f) {
            return null;
        }
        float e = easeOut(t);
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (from.isEdge()) {
            double aspect = (double) mc.getMainRenderTarget().width / Math.max(1, mc.getMainRenderTarget().height);
            return from.edge().worldEntry(planeCenter, camera, aspect, e);
        }
        Vec3 cam = camera.getPosition();
        Vector3f start = new Vector3f((float) (from.point().x - cam.x), (float) (from.point().y - cam.y),
                (float) (from.point().z - cam.z));
        return new Matrix4f().translation(start.lerp(planeCenter, e)).scale(Mth.lerp(e, START_SCALE, 1f))
                .translate(-planeCenter.x(), -planeCenter.y(), -planeCenter.z());
    }

    public static float spatialAlpha() {
        if (screen == null || !spatial) {
            return 1f;
        }
        return Math.min(1f, progress() * 2f);
    }

    public static String describeProgress() {
        Minecraft mc = Minecraft.getInstance();
        return String.format(java.util.Locale.ROOT, "ticks=%d partial=%.3f start=%.3f progress=%.3f", ticks,
                EffectClock.partialTick(), startTime, progress());
    }

    private static float progress() {
        if (screen == null) {
            return 1f;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen) {
            stop();
            return 1f;
        }
        double now = ticks + EffectClock.partialTick();
        if (Double.isNaN(startTime)) {
            startTime = now;
        }
        float t = (float) ((now - startTime) / duration);
        if (t >= 1f) {
            stop();
            return 1f;
        }
        return Math.max(0f, t);
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }

    private static float[] projectToGui(Vec3 world, int guiWidth, int guiHeight) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 d = world.subtract(camera.getPosition());
        Vector3fc forward = camera.getLookVector();
        Vector3fc up = camera.getUpVector();
        Vector3fc left = camera.getLeftVector();
        double depth = d.x * forward.x() + d.y * forward.y() + d.z * forward.z();
        double right = -(d.x * left.x() + d.y * left.y() + d.z * left.z());
        double above = d.x * up.x() + d.y * up.y() + d.z * up.z();
        double tanHalf = Math.tan(Math.toRadians(net.shattergui.shatter.CameraFov.of(camera)) / 2);
        double aspect = (double) mc.getMainRenderTarget().width / Math.max(1, mc.getMainRenderTarget().height);
        float ndcX;
        float ndcY;
        if (depth > MIN_DEPTH) {
            ndcX = (float) (right / (depth * tanHalf * aspect));
            ndcY = (float) (above / (depth * tanHalf));
        } else {
            double length = Math.max(1e-6, Math.hypot(right, above));
            ndcX = (float) (right / length * MAX_NDC);
            ndcY = (float) (above / length * MAX_NDC);
        }
        ndcX = Math.clamp(ndcX, -MAX_NDC, MAX_NDC);
        ndcY = Math.clamp(ndcY, -MAX_NDC, MAX_NDC);
        return new float[]{(ndcX + 1f) / 2f * guiWidth, (1f - ndcY) / 2f * guiHeight};
    }
}
