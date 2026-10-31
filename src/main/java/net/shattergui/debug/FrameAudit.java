package net.shattergui.debug;

import net.minecraft.client.Minecraft;
import net.shattergui.compat.spatial.SpatialGuiCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * What showed the interface in each frame, for tests: the flat screen, Spatial GUI's plane (and its opacity), and our
 * waves that drew something — openings (gathering) and closes. An interface must be on screen at most once at a time:
 * {@link Frame#instances}. Off unless a test turns it on; then one small record per frame, the last {@link #KEPT}.
 */
public final class FrameAudit {
    private static final int KEPT = 2000;

    /** One frame. {@code planeAlpha}: Spatial GUI's plane opacity, or −1 if it was not drawn. */
    public record Frame(long index, boolean screenOpen, boolean flat, float planeAlpha, int openings, int closes) {
        /** How many pictures of the interface were visible. */
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

    /** The open screen was extracted to be drawn (not hidden, not a capture). */
    public static void screenExtracted() {
        screenExtracted = true;
    }

    /** Spatial GUI drew its plane with this opacity. */
    public static void plane(float alpha) {
        planeAlpha = Math.max(planeAlpha, alpha);
    }

    /** A wave drew shards this frame. */
    public static void wave(boolean opening) {
        if (opening) {
            openings++;
        } else {
            closes++;
        }
    }

    /** End of {@code GameRenderer#render}. */
    public static void endFrame() {
        if (!enabled) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.screen != null;
        // Spatial GUI extracts the screen the same way to draw it into its texture: that is its plane, not the flat GUI.
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

    /** Index the next frame will get. */
    public static long next() {
        return index;
    }

    /** The frames with index ≥ {@code from}. */
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
