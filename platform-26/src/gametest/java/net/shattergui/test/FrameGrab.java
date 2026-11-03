package net.shattergui.test;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Screenshot;
import net.shattergui.ShatterGuiClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frames of the game read back for automatic checks: the last drawn frame of the main target (vanilla
 * {@link Screenshot#takeScreenshot}, which works with Iris too), shrunk to a {@link #W}×{@link #H} grid of averaged
 * cells. Requests return at once; the picture arrives a frame or two later ({@link #get}). Optionally saved as PNG in
 * the run's screenshots folder for looking at.
 */
final class FrameGrab {
    static final int W = 192;
    static final int H = 108;
    private static final Map<String, Frame> RESULTS = new ConcurrentHashMap<>();

    /** A shrunk frame: RGB per cell. */
    record Frame(int[] rgb) {
    }

    private FrameGrab() {
    }

    static void request(ClientGameTestContext context, String name, boolean save) {
        RESULTS.remove(name);
        context.runOnClient(mc -> Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            try {
                RESULTS.put(name, new Frame(shrink(image)));
                if (save) {
                    Path dir = mc.gameDirectory.toPath().resolve("screenshots");
                    Files.createDirectories(dir);
                    image.writeToFile(dir.resolve(name + ".png"));
                }
            } catch (Exception e) {
                ShatterGuiClient.LOGGER.warn("[ShatterTest] could not grab frame {}", name, e);
            } finally {
                if (!image.isClosed()) {
                    image.close();
                }
            }
        }));
    }

    /** The frame requested as {@code name}, waiting for it up to 40 ticks; null if it never came. */
    static Frame get(ClientGameTestContext context, String name) {
        for (int i = 0; i < 40 && !RESULTS.containsKey(name); i++) {
            context.waitTick();
        }
        return RESULTS.remove(name);
    }

    private static int[] shrink(NativeImage image) {
        int[] out = new int[W * H * 3];
        int iw = image.getWidth();
        int ih = image.getHeight();
        for (int cy = 0; cy < H; cy++) {
            int y0 = cy * ih / H;
            int y1 = Math.max(y0 + 1, (cy + 1) * ih / H);
            for (int cx = 0; cx < W; cx++) {
                int x0 = cx * iw / W;
                int x1 = Math.max(x0 + 1, (cx + 1) * iw / W);
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                // Every other pixel is plenty for the average.
                for (int y = y0; y < y1; y += 2) {
                    for (int x = x0; x < x1; x += 2) {
                        int c = image.getPixel(x, y);
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        n++;
                    }
                }
                int o = (cy * W + cx) * 3;
                out[o] = (int) (r / n);
                out[o + 1] = (int) (g / n);
                out[o + 2] = (int) (b / n);
            }
        }
        return out;
    }

    /** Share of cells whose colour differs by more than {@code threshold} in some channel. */
    static double changed(Frame a, Frame b, int threshold) {
        return changed(a, b, threshold, new double[]{0, 0, 1, 1});
    }

    /** As above, within a part of the frame (fractions x0, y0, x1, y1). */
    static double changed(Frame a, Frame b, int threshold, double[] area) {
        if (a == null || b == null) {
            return Double.NaN;
        }
        int cx0 = (int) Math.floor(area[0] * W);
        int cy0 = (int) Math.floor(area[1] * H);
        int cx1 = Math.max(cx0 + 1, (int) Math.ceil(area[2] * W));
        int cy1 = Math.max(cy0 + 1, (int) Math.ceil(area[3] * H));
        int count = 0;
        int cells = 0;
        for (int y = Math.max(0, cy0); y < Math.min(H, cy1); y++) {
            for (int x = Math.max(0, cx0); x < Math.min(W, cx1); x++) {
                cells++;
                int o = (y * W + x) * 3;
                int d = Math.max(Math.abs(a.rgb[o] - b.rgb[o]), Math.max(Math.abs(a.rgb[o + 1] - b.rgb[o + 1]),
                        Math.abs(a.rgb[o + 2] - b.rgb[o + 2])));
                if (d > threshold) {
                    count++;
                }
            }
        }
        return count / (double) Math.max(1, cells);
    }

    /**
     * Share of cells of {@code a} that match no cell of {@code b} within one cell around (by more than {@code threshold}
     * in some channel). A shift of a pixel or two (resampling, a camera still easing) does not count; a picture that
     * moved further or changed brightness does.
     */
    static double changedBeyondShift(Frame a, Frame b, int threshold) {
        return changedBeyondShift(a, b, threshold, new double[]{0, 0, 1, 1});
    }

    /** As above, within a part of the frame (fractions x0, y0, x1, y1). */
    static double changedBeyondShift(Frame a, Frame b, int threshold, double[] area) {
        if (a == null || b == null) {
            return Double.NaN;
        }
        int count = 0;
        int cells = 0;
        int ax0 = Math.max(0, (int) Math.floor(area[0] * W));
        int ay0 = Math.max(0, (int) Math.floor(area[1] * H));
        int ax1 = Math.min(W, Math.max(ax0 + 1, (int) Math.ceil(area[2] * W)));
        int ay1 = Math.min(H, Math.max(ay0 + 1, (int) Math.ceil(area[3] * H)));
        for (int y = ay0; y < ay1; y++) {
            for (int x = ax0; x < ax1; x++) {
                cells++;
                int o = (y * W + x) * 3;
                int best = Integer.MAX_VALUE;
                for (int dy = -1; dy <= 1 && best > threshold; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= W || ny >= H) {
                            continue;
                        }
                        int q = (ny * W + nx) * 3;
                        int d = Math.max(Math.abs(a.rgb[o] - b.rgb[q]), Math.max(Math.abs(a.rgb[o + 1] - b.rgb[q + 1]),
                                Math.abs(a.rgb[o + 2] - b.rgb[q + 2])));
                        best = Math.min(best, d);
                    }
                }
                if (best > threshold) {
                    count++;
                }
            }
        }
        return count / (double) Math.max(1, cells);
    }

    /**
     * Bounding box (cells: x0, y0, x1, y1, exclusive end) of the cells where {@code a} differs from {@code background},
     * or null if none.
     */
    static int[] box(Frame a, Frame background, int threshold) {
        int x0 = W;
        int y0 = H;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int o = (y * W + x) * 3;
                int d = Math.max(Math.abs(a.rgb[o] - background.rgb[o]), Math.max(Math.abs(a.rgb[o + 1]
                        - background.rgb[o + 1]), Math.abs(a.rgb[o + 2] - background.rgb[o + 2])));
                if (d > threshold) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x + 1);
                    y1 = Math.max(y1, y + 1);
                }
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
    }
}
