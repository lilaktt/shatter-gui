package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.shattergui.ShatterGuiClient;
import net.shattergui.platform.impl.Mc26;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

final class FullFrame {
    record Image(int width, int height, int[] argb) {
        int at(int x, int y) {
            return argb[y * width + x];
        }
    }

    record Blob(int count, double x, double y) {
    }

    private static final Map<String, Image> RESULTS = new ConcurrentHashMap<>();

    private FullFrame() {
    }

    static void request(ClientGameTestContext context, String name, boolean save) {
        context.runOnClient(mc -> grab(mc, name, save));
    }

    static void grab(Minecraft mc, String name, boolean save) {
        grab(mc, name, save, null);
    }

    private static final Map<String, Object> ANALYSES = new ConcurrentHashMap<>();

    static void grab(Minecraft mc, String name, boolean save, java.util.function.Function<Image, Object> analyze) {
        RESULTS.remove(name);
        ANALYSES.remove(name);
        Screenshot.takeScreenshot(Mc26.mainTarget(), image -> {
            try {
                int w = image.getWidth();
                int h = image.getHeight();
                int[] argb = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        argb[y * w + x] = image.getPixel(x, y);
                    }
                }
                if (analyze != null) {
                    ANALYSES.put(name, analyze.apply(new Image(w, h, argb)));
                } else {
                    RESULTS.put(name, new Image(w, h, argb));
                }
                if (save) {
                    Path dir = mc.gameDirectory.toPath().resolve("screenshots");
                    Files.createDirectories(dir);
                    image.writeToFile(dir.resolve(name + ".png"));
                }
            } catch (Exception e) {
                ShatterGuiClient.LOGGER.warn("[ShatterTest] could not read frame {}", name, e);
            } finally {
                if (!image.isClosed()) {
                    image.close();
                }
            }
        });
    }

    static Image get(ClientGameTestContext context, String name) {
        for (int i = 0; i < 60 && !RESULTS.containsKey(name); i++) {
            context.waitTick();
        }
        return RESULTS.remove(name);
    }

    static Object analysis(ClientGameTestContext context, String name) {
        for (int i = 0; i < 60 && !ANALYSES.containsKey(name); i++) {
            context.waitTick();
        }
        return ANALYSES.remove(name);
    }

    static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    static int b(int c) {
        return c & 0xFF;
    }

    static boolean magenta(int c) {
        int r = r(c);
        int g = g(c);
        int b = b(c);
        int lo = Math.min(r, b);
        int hi = Math.max(r, b);
        return lo >= 45 && g * 2 < lo && hi - lo <= hi * 0.35;
    }

    static boolean red(int c) {
        int r = r(c);
        int g = g(c);
        int b = b(c);
        return r >= 60 && g * 2 < r && b * 2 < r;
    }

    static Blob blob(Image image, IntPredicate test, double[] area) {
        int x0 = Math.max(0, (int) Math.floor(area[0] * image.width));
        int y0 = Math.max(0, (int) Math.floor(area[1] * image.height));
        int x1 = Math.min(image.width, (int) Math.ceil(area[2] * image.width));
        int y1 = Math.min(image.height, (int) Math.ceil(area[3] * image.height));
        long count = 0;
        double sx = 0;
        double sy = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (test.test(image.at(x, y))) {
                    count++;
                    sx += x + 0.5;
                    sy += y + 0.5;
                }
            }
        }
        return count == 0 ? new Blob(0, Double.NaN, Double.NaN) : new Blob((int) count, sx / count, sy / count);
    }

    static Blob weighted(Image image, java.util.function.IntToDoubleFunction weight, double[] area) {
        int x0 = Math.max(0, (int) Math.floor(area[0] * image.width));
        int y0 = Math.max(0, (int) Math.floor(area[1] * image.height));
        int x1 = Math.min(image.width, (int) Math.ceil(area[2] * image.width));
        int y1 = Math.min(image.height, (int) Math.ceil(area[3] * image.height));
        double sum = 0;
        double sx = 0;
        double sy = 0;
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double w = weight.applyAsDouble(image.at(x, y));
                if (w > 0) {
                    sum += w;
                    sx += (x + 0.5) * w;
                    sy += (y + 0.5) * w;
                    count++;
                }
            }
        }
        return sum <= 0 ? new Blob(0, Double.NaN, Double.NaN) : new Blob(count, sx / sum, sy / sum);
    }

    static double magentaWeight(int c) {
        int r = r(c);
        int g = g(c);
        int b = b(c);
        int lo = Math.min(r, b);
        int hi = Math.max(r, b);
        double strength = Math.clamp((lo - g * 1.5 - 20) / 60.0, 0, 1);
        double balance = hi == 0 ? 0 : Math.clamp(1 - (hi - lo) / (0.5 * hi), 0, 1);
        return strength * balance;
    }

    static double redWeight(int c) {
        int r = r(c);
        int other = Math.max(g(c), b(c));
        return Math.clamp((r - other * 1.6 - 15) / 50.0, 0, 1);
    }

    static Blob blobAround(Image image, IntPredicate test, double cx, double cy, double radius) {
        long count = 0;
        double sx = 0;
        double sy = 0;
        int x0 = Math.max(0, (int) Math.floor(cx - radius));
        int y0 = Math.max(0, (int) Math.floor(cy - radius));
        int x1 = Math.min(image.width, (int) Math.ceil(cx + radius));
        int y1 = Math.min(image.height, (int) Math.ceil(cy + radius));
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                if (dx * dx + dy * dy <= radius * radius && test.test(image.at(x, y))) {
                    count++;
                    sx += x + 0.5;
                    sy += y + 0.5;
                }
            }
        }
        return count == 0 ? new Blob(0, Double.NaN, Double.NaN) : new Blob((int) count, sx / count, sy / count);
    }

    static double[] changed(Image a, Image b, int threshold, double[] area, IntPredicate where) {
        if (a == null || b == null || a.width != b.width || a.height != b.height) {
            return new double[]{Double.NaN, Double.NaN, 0};
        }
        int x0 = Math.max(0, (int) Math.floor(area[0] * a.width));
        int y0 = Math.max(0, (int) Math.floor(area[1] * a.height));
        int x1 = Math.min(a.width, (int) Math.ceil(area[2] * a.width));
        int y1 = Math.min(a.height, (int) Math.ceil(area[3] * a.height));
        long count = 0;
        long cells = 0;
        int worst = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int ca = a.at(x, y);
                int cb = b.at(x, y);
                if (where != null && !where.test(ca) && !where.test(cb)) {
                    continue;
                }
                cells++;
                int d = Math.max(Math.abs(r(ca) - r(cb)), Math.max(Math.abs(g(ca) - g(cb)), Math.abs(b(ca) - b(cb))));
                worst = Math.max(worst, d);
                if (d > threshold) {
                    count++;
                }
            }
        }
        return new double[]{cells == 0 ? 0 : count / (double) cells, worst, cells};
    }
}
