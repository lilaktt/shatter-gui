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

/**
 * The GUI of a screen that just opened flies out of the container it was opened from: it starts small at the
 * container and eases out to its normal place and size within {@code flyDurationTicks}. A screen without a container
 * flies in from beyond an edge or corner of the screen instead, slightly rolled and smaller, straightening out as it
 * lands.
 *
 * <ul>
 *   <li>Flat GUI: the container is projected onto the screen and the GUI's pose is moved and scaled from there
 *       ({@link #pushFlatTransform}); the darkened background stays in place.</li>
 *   <li>Spatial GUI: its plane is moved and scaled from the container in camera-relative world space
 *       ({@link #spatialMatrix}) and faded in ({@link #spatialAlpha}); its own open animation is cancelled.</li>
 * </ul>
 *
 * <p>Time is client ticks + partial tick ({@link EffectClock}), so the animation is smooth, runs at the same speed as
 * the shards and also plays while the game is paused (the pause menu).
 * While it runs, clicks are ignored and nothing is hovered (the GUI is not where the mouse coordinates point).
 */
public final class FlyOpen {
    /** Size of the GUI at the container, relative to its normal size. */
    private static final float START_SCALE = 0.05f;
    /** Projected container points are kept within this many screen halves of the centre (behind the camera, far off screen). */
    private static final float MAX_NDC = 1.5f;
    /** Closer than this in front of the camera, the projection is replaced by a direction towards the screen edge. */
    private static final double MIN_DEPTH = 0.05;

    private static Screen screen;
    private static FlyTarget from;
    private static boolean spatial;
    /** Whether the end of the flight is where the screen is in this frame (else where it was on the first frame). */
    private static boolean follows = true;
    /** Not following: the end of the flight in world coordinates, fixed on the first frame. */
    private static Vec3 fixedEnd;
    private static boolean startLogged;
    private static boolean drawingFlat;
    private static int duration;
    /** Game time of the first frame of the animation; NaN until that frame. */
    private static double startTime = Double.NaN;
    private static long ticks;

    private FlyOpen() {
    }

    /** @param origin the container (point), or, for the fly effect on screens without one, the screen edge */
    public static void start(Screen opened, FlyTarget origin, boolean onSpatialPlane, int durationTicks) {
        start(opened, origin, onSpatialPlane, durationTicks, true);
    }

    /** {@code followCamera}: the flight ends where the screen is in each frame, in front of the moving camera. */
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

    /** A fly-in is running (for the limit of effects at once). */
    public static boolean running() {
        return screen != null;
    }

    public static void stop() {
        screen = null;
        from = null;
    }

    /** END_CLIENT_TICK, also while the game is paused. */
    public static void tick() {
        ticks++;
    }

    /** True while {@code s} is still flying in: its input is ignored and nothing in it is hovered. */
    public static boolean isAnimating(Screen s) {
        return s != null && s == screen && progress() < 1f;
    }

    /**
     * HEAD of {@code Screen#extractRenderStateWithTooltipAndSubtitles}: moves and scales the flat GUI. Returns true if a
     * matrix was pushed, which the caller pops at RETURN.
     */
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
            // In the world: from the container to the point in front of the camera where the screen is (this frame's
            // camera when following it), projected with this frame's camera. At the end it is that point exactly.
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

    /** RETURN of {@code Screen#extractRenderStateWithTooltipAndSubtitles}, if {@link #pushFlatTransform} pushed. */
    public static void popFlatTransform(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
        drawingFlat = false;
    }

    /**
     * True while a flying flat GUI is being extracted. Pictures in pictures (the player model in the inventory) are
     * placed by fixed GUI coordinates and ignore the pose, so they would stay in place while the GUI flies: they are
     * left out of those frames and appear when it lands.
     */
    public static boolean hidesPictureInPicture() {
        return drawingFlat;
    }

    /**
     * For Spatial GUI's plane: the transform to put in front of its own, in camera-relative world space, given where its
     * plane's centre is now ({@code planeCenter}, relative to the camera). Null when no animation runs on its plane.
     */
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
        // The plane's centre this frame (Spatial GUI keeps it in front of its camera); not following the camera: where
        // it was on the first frame, in the world.
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

    /** Opacity of Spatial GUI's plane while it flies in (multiplies its own). */
    public static float spatialAlpha() {
        if (screen == null || !spatial) {
            return 1f;
        }
        return Math.min(1f, progress() * 2f);
    }

    /** How far {@code s} has flown in, 0..1 (1 when it is not flying in). */
    public static float progress(Screen s) {
        return s != null && s == screen ? progress() : 1f;
    }

    /** For tests and the profile log: game time now and the animation's progress (1 = none running). */
    public static String describeProgress() {
        Minecraft mc = Minecraft.getInstance();
        return String.format(java.util.Locale.ROOT, "ticks=%d partial=%.3f start=%.3f progress=%.3f", ticks,
                EffectClock.partialTick(), startTime, progress());
    }

    /** 0 at the first frame, 1 when done (and from then on, also with no animation at all). */
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

    /** Ease-out (quadratic): fast out of the container, slowing down into place. */
    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }

    /**
     * The point on the flat GUI's plane (1 block in front of the camera, see {@code PlaneBasis}) that shows at GUI
     * coordinates (x, y): where the real screen is in the world, relative to the camera.
     */
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

    /** A point relative to the camera → GUI coordinates, kept near the screen if it is off screen or behind. */
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
            // Beside or behind the camera: come in from the screen edge on that side.
            double length = Math.max(1e-6, Math.hypot(right, above));
            ndcX = (float) (right / length * MAX_NDC);
            ndcY = (float) (above / length * MAX_NDC);
        }
        ndcX = Math.clamp(ndcX, -MAX_NDC, MAX_NDC);
        ndcY = Math.clamp(ndcY, -MAX_NDC, MAX_NDC);
        return new float[]{(ndcX + 1f) / 2f * guiWidth, (1f - ndcY) / 2f * guiHeight};
    }
}
