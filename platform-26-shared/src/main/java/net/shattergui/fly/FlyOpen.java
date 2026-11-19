package net.shattergui.fly;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.shattergui.mixin.AbstractContainerScreenAccessor;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.EffectClock;
import org.joml.Matrix3x2fStack;
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
    private static boolean follows = true;
    private static Vec3 fixedEnd;
    private static boolean startLogged;
    private static boolean drawingFlat;
    private static int duration;
    private static double startTime = Double.NaN;
    private static long ticks;

    private FlyOpen() {
    }

    public static void start(Screen opened, FlyTarget origin, boolean onSpatialPlane, int durationTicks) {
        start(opened, origin, onSpatialPlane, durationTicks, true);
    }

    public static void start(Screen opened, FlyTarget origin, boolean onSpatialPlane, int durationTicks,
                             boolean followCamera) {
        screen = opened;
        from = origin;
        spatial = onSpatialPlane;
        duration = Math.max(1, durationTicks);
        startTime = Double.NaN;
        follows = followCamera;
        fixedEnd = null;
        startLogged = false;
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

    public static boolean pushFlatTransform(Screen s, GuiGraphicsExtractor graphics) {
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
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if (from.isEdge()) {
            from.edge().applyFlatEntry(pose, centerX, centerY, s.width, s.height, e);
        } else {
            Camera camera = Mc26.camera();
            Vec3 cam = camera.position();
            Vector3f end = screenPoint(camera, centerX, centerY, s.width, s.height);
            if (!follows) {
                if (fixedEnd == null) {
                    fixedEnd = cam.add(end.x(), end.y(), end.z());
                }
                end.set((float) (fixedEnd.x - cam.x), (float) (fixedEnd.y - cam.y), (float) (fixedEnd.z - cam.z));
            }
            Vector3f source = new Vector3f((float) (from.point().x - cam.x), (float) (from.point().y - cam.y),
                    (float) (from.point().z - cam.z));
            Vector3f at = source.lerp(end, e, new Vector3f());
            float[] gui = projectToGui(at, s.width, s.height);
            if (!startLogged) {
                startLogged = true;
                float[] first = projectToGui(new Vector3f((float) (from.point().x - cam.x),
                        (float) (from.point().y - cam.y), (float) (from.point().z - cam.z)), s.width, s.height);
                net.shattergui.debug.ShatterProfiler.openValue("fly.start", String.format(java.util.Locale.ROOT,
                        "container at GUI %.0f, %.0f of %dx%d (%.2f blocks ahead), screen centre %.0f, %.0f",
                        first[0], first[1], s.width, s.height, source.dot(camera.forwardVector()), centerX, centerY));
            }
            net.shattergui.debug.FrameAudit.openPlane(at);
            pose.translate(gui[0], gui[1]);
            pose.scale(Mth.lerp(e, START_SCALE, 1f));
            pose.translate(-centerX, -centerY);
        }
        drawingFlat = true;
        return true;
    }

    public static void popFlatTransform(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
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
        Camera camera = Mc26.camera();
        if (from.isEdge()) {
            double aspect = (double) Mc26.mainTarget().width / Math.max(1, Mc26.mainTarget().height);
            return from.edge().worldEntry(planeCenter, camera, aspect, e);
        }
        Vec3 cam = camera.position();
        Vector3f start = new Vector3f((float) (from.point().x - cam.x), (float) (from.point().y - cam.y),
                (float) (from.point().z - cam.z));
        Vector3f end = new Vector3f(planeCenter);
        if (!follows) {
            if (fixedEnd == null) {
                fixedEnd = cam.add(planeCenter.x(), planeCenter.y(), planeCenter.z());
            }
            end.set((float) (fixedEnd.x - cam.x), (float) (fixedEnd.y - cam.y), (float) (fixedEnd.z - cam.z));
        }
        Vector3f at = start.lerp(end, e);
        net.shattergui.debug.FrameAudit.openPlane(at);
        return new Matrix4f().translation(at).scale(Mth.lerp(e, START_SCALE, 1f))
                .translate(-planeCenter.x(), -planeCenter.y(), -planeCenter.z());
    }

    public static float spatialAlpha() {
        if (screen == null || !spatial) {
            return 1f;
        }
        return Math.min(1f, progress() * 2f);
    }

    public static float progress(Screen s) {
        return s != null && s == screen ? progress() : 1f;
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
        if (Mc26.screen() != screen) {
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

    private static Vector3f screenPoint(Camera camera, float x, float y, int guiWidth, int guiHeight) {
        Minecraft mc = Minecraft.getInstance();
        double tanHalf = Math.tan(Math.toRadians(camera.getFov()) / 2);
        double aspect = (double) Mc26.mainTarget().width / Math.max(1, Mc26.mainTarget().height);
        float right = (float) ((x / guiWidth * 2 - 1) * tanHalf * aspect);
        float above = (float) ((1 - y / guiHeight * 2) * tanHalf);
        Vector3f out = new Vector3f(camera.forwardVector());
        out.add(new Vector3f(camera.leftVector()).mul(-right)).add(new Vector3f(camera.upVector()).mul(above));
        return out;
    }

    private static float[] projectToGui(Vector3fc d, int guiWidth, int guiHeight) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = Mc26.camera();
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        double depth = d.x() * forward.x() + d.y() * forward.y() + d.z() * forward.z();
        double right = -(d.x() * left.x() + d.y() * left.y() + d.z() * left.z());
        double above = d.x() * up.x() + d.y() * up.y() + d.z() * up.z();
        double tanHalf = Math.tan(Math.toRadians(camera.getFov()) / 2);
        double aspect = (double) Mc26.mainTarget().width / Math.max(1, Mc26.mainTarget().height);
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
