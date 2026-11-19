package net.shattergui.debug;

import net.minecraft.client.Minecraft;
import net.shattergui.compat.spatial.SpatialGuiCompat;

import java.util.ArrayList;
import java.util.List;

public final class FrameAudit {
    private static final int KEPT = 2000;

    public record Frame(long index, boolean screenOpen, boolean flat, float planeAlpha, int openings, int closes) {
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

    public static void wave(boolean opening) {
        if (opening) {
            openings++;
        } else {
            closes++;
        }
    }

    public static void endFrame() {
        if (!enabled) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.screen != null;
        boolean flat = screenExtracted && open && !SpatialGuiCompat.isShowing(mc.screen);
        FRAMES.add(new Frame(index++, open, flat, planeAlpha, openings, closes));
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
