package net.shattergui.debug;

import net.minecraft.client.Minecraft;
import net.shattergui.compat.spatial.SpatialGuiCompat;

import java.util.ArrayList;
import java.util.List;
import net.shattergui.platform.impl.Mc26;

public final class FrameAudit {
    private static final int KEPT = 2000;

    public record Frame(long index, boolean screenOpen, boolean flat, float planeAlpha, int openings, int closes,
                        float[] plane, float backdrop, float[] spatialPlane, double time) {
        public int instances() {
            return (flat ? 1 : 0) + (planeAlpha > 0.01f ? 1 : 0) + openings + closes;
        }

        public boolean planeVisible() {
            return planeAlpha > 0.01f;
        }
    }

    private static boolean enabled;
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static long index;
    private static boolean screenExtracted;
    private static float planeAlpha = -1;
    private static int openings;
    private static int closes;
    private static float[] plane;

    private FrameAudit() {
    }

    public static void enable(boolean on) {
        enabled = on;
        FRAMES.clear();
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void screenExtracted() {
        screenExtracted = true;
    }

    public static void plane(float alpha) {
        planeAlpha = Math.max(planeAlpha, alpha);
    }

    public static void openPlane(org.joml.Vector3fc relative) {
        if (!enabled) {
            return;
        }
        plane = local(relative.x(), relative.y(), relative.z());
    }

    private static float[] local(double x, double y, double z) {
        net.minecraft.client.Camera camera = Mc26.camera();
        org.joml.Vector3fc left = camera.leftVector();
        org.joml.Vector3fc up = camera.upVector();
        org.joml.Vector3fc forward = camera.forwardVector();
        return new float[]{(float) -(x * left.x() + y * left.y() + z * left.z()),
                (float) (x * up.x() + y * up.y() + z * up.z()), (float) (x * forward.x() + y * forward.y() + z * forward.z())};
    }

    public static void wave(boolean opening) {
        if (opening) {
            openings++;
        } else {
            closes++;
        }
    }

    private static final List<Runnable> AT_END = new ArrayList<>();

    public static void atFrameEnd(Runnable action) {
        AT_END.add(action);
    }

    public static void endFrame() {
        if (!AT_END.isEmpty()) {
            List<Runnable> actions = new ArrayList<>(AT_END);
            AT_END.clear();
            actions.forEach(Runnable::run);
        }
        if (!enabled) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean open = Mc26.screen() != null;
        boolean flat = screenExtracted && open && !SpatialGuiCompat.isShowing(Mc26.screen());
        org.joml.Vector3d spatial = open && SpatialGuiCompat.isShowing(Mc26.screen()) ? SpatialGuiCompat.planeCenterNow() : null;
        FRAMES.add(new Frame(index++, open, flat, planeAlpha, openings, closes, plane,
                net.shattergui.fly.OpenBackdrop.shown(), spatial == null ? null : local(spatial.x, spatial.y, spatial.z),
                net.shattergui.shatter.EffectClock.now()));
        if (FRAMES.size() > KEPT) {
            FRAMES.removeFirst();
        }
        reset();
    }

    private static void reset() {
        screenExtracted = false;
        planeAlpha = -1;
        openings = 0;
        closes = 0;
        plane = null;
    }

    public static long next() {
        return index;
    }

    public static List<Frame> since(long from) {
        List<Frame> out = new ArrayList<>();
        for (Frame f : FRAMES) {
            if (f.index() >= from) {
                out.add(f);
            }
        }
        return out;
    }
}
