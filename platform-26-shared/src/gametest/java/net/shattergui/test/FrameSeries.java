package net.shattergui.test;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.shattergui.ShatterGuiClient;
import net.shattergui.debug.FrameAudit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class FrameSeries {
    static final int TW = 320;
    static final int TH = 180;
    static final double[] VIEW = {0, 0, 1, 0.86};

    record Shot(int index, long nanos, long audit, Object state, int magenta, int[] box, double cx, double cy,
                FrameGrab.Frame grid, int[] thumb) {
        double boxWidth() {
            return box == null ? 0 : box[2] - box[0];
        }

        double boxHeight() {
            return box == null ? 0 : box[3] - box[1];
        }
    }

    private FrameSeries() {
    }

    static List<Shot> record(ClientGameTestContext context, int before, Runnable action, Supplier<Object> state,
                             BooleanSupplier done, int max) {
        return record(context, before, action, null, state, done, max);
    }

    static List<Shot> record(ClientGameTestContext context, int before, Runnable action, Runnable fromTest,
                             Supplier<Object> state, BooleanSupplier done, int max) {
        List<Shot> out = new CopyOnWriteArrayList<>();
        int[] requested = {0};
        int[] finished = {0};
        boolean[] stopped = {false};
        context.runOnClient(mc -> {
            Runnable[] step = new Runnable[1];
            step[0] = () -> {
                int index = requested[0]++;
                long nanos = System.nanoTime();
                long audit = FrameAudit.next();
                Object sample = state == null ? null : state.get();
                FullFrame.grab(mc, "series_" + System.identityHashCode(out) + "_" + index, false, image -> {
                    out.add(analyse(index, nanos, audit, sample, image));
                    synchronized (finished) {
                        finished[0]++;
                    }
                    return Boolean.TRUE;
                });
                if (index == before - 1 && action != null) {
                    action.run();
                }
                boolean enough = index >= before && (done == null || done.getAsBoolean());
                if (requested[0] < max && !enough) {
                    FrameAudit.atFrameEnd(step[0]);
                } else {
                    synchronized (finished) {
                        stopped[0] = true;
                    }
                }
            };
            FrameAudit.atFrameEnd(step[0]);
        });
        if (fromTest != null) {
            for (int i = 0; i < 100; i++) {
                synchronized (finished) {
                    if (requested[0] >= before) {
                        break;
                    }
                }
                context.waitTick();
            }
            fromTest.run();
        }
        for (int i = 0; i < 1200; i++) {
            synchronized (finished) {
                if (stopped[0] && finished[0] >= requested[0]) {
                    break;
                }
            }
            context.waitTick();
        }
        List<Shot> sorted = new ArrayList<>(out);
        sorted.sort(java.util.Comparator.comparingInt(Shot::index));
        return sorted;
    }

    private static Shot analyse(int index, long nanos, long audit, Object state, FullFrame.Image image) {
        int w = image.width();
        int h = image.height();
        int x1v = (int) Math.ceil(VIEW[2] * w);
        int y1v = (int) Math.ceil(VIEW[3] * h);
        int count = 0;
        int bx0 = w;
        int by0 = h;
        int bx1 = -1;
        int by1 = -1;
        double sx = 0;
        double sy = 0;
        for (int y = 0; y < y1v; y++) {
            for (int x = 0; x < x1v; x++) {
                if (FullFrame.magenta(image.at(x, y))) {
                    count++;
                    sx += x + 0.5;
                    sy += y + 0.5;
                    bx0 = Math.min(bx0, x);
                    by0 = Math.min(by0, y);
                    bx1 = Math.max(bx1, x + 1);
                    by1 = Math.max(by1, y + 1);
                }
            }
        }
        int[] box = count == 0 ? null : new int[]{bx0, by0, bx1, by1};
        int[] grid = new int[FrameGrab.W * FrameGrab.H * 3];
        for (int cy = 0; cy < FrameGrab.H; cy++) {
            int y0 = cy * h / FrameGrab.H;
            int y1 = Math.max(y0 + 1, (cy + 1) * h / FrameGrab.H);
            for (int cx = 0; cx < FrameGrab.W; cx++) {
                int x0 = cx * w / FrameGrab.W;
                int x1 = Math.max(x0 + 1, (cx + 1) * w / FrameGrab.W);
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                for (int y = y0; y < y1; y += 2) {
                    for (int x = x0; x < x1; x += 2) {
                        int c = image.at(x, y);
                        r += FullFrame.r(c);
                        g += FullFrame.g(c);
                        b += FullFrame.b(c);
                        n++;
                    }
                }
                int o = (cy * FrameGrab.W + cx) * 3;
                grid[o] = (int) (r / n);
                grid[o + 1] = (int) (g / n);
                grid[o + 2] = (int) (b / n);
            }
        }
        int[] thumb = new int[TW * TH];
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                thumb[y * TW + x] = image.at(Math.min(w - 1, (int) ((x + 0.5) * w / TW)), Math.min(h - 1, (int) ((y + 0.5) * h / TH)));
            }
        }
        return new Shot(index, nanos, audit, state, count, box, count == 0 ? Double.NaN : sx / count / w,
                count == 0 ? Double.NaN : sy / count / h, new FrameGrab.Frame(grid), thumb);
    }

    static List<Shot> pickNine(List<Shot> shots, int from, int to) {
        List<Shot> nine = new ArrayList<>();
        int a = Math.max(0, from);
        int b = Math.max(a, Math.min(shots.size() - 1, to));
        for (int k = 0; k < 9; k++) {
            nine.add(shots.get(a + (int) Math.round((b - a) * k / 8.0)));
        }
        return nine;
    }

    static void saveStrip(ClientGameTestContext context, List<Shot> nine, String name) {
        int gap = 4;
        int width = TW * 3 + gap * 4;
        int height = TH * 3 + gap * 4;
        context.runOnClient(mc -> {
            try (NativeImage image = new NativeImage(width, height, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixel(x, y, 0xFF202020);
                    }
                }
                for (int k = 0; k < nine.size(); k++) {
                    int ox = gap + (k % 3) * (TW + gap);
                    int oy = gap + (k / 3) * (TH + gap);
                    int[] thumb = nine.get(k).thumb();
                    for (int y = 0; y < TH; y++) {
                        for (int x = 0; x < TW; x++) {
                            image.setPixel(ox + x, oy + y, 0xFF000000 | thumb[y * TW + x]);
                        }
                    }
                }
                Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("strips");
                Files.createDirectories(dir);
                image.writeToFile(dir.resolve(name + ".png"));
            } catch (Exception e) {
                ShatterGuiClient.LOGGER.warn("[ShatterTest] could not save the strip {}", name, e);
            }
        });
    }

    static String describe(List<Shot> shots) {
        StringBuilder out = new StringBuilder();
        long t0 = shots.isEmpty() ? 0 : shots.getFirst().nanos();
        for (Shot s : shots) {
            out.append(String.format(java.util.Locale.ROOT, "%n    #%d %+.0f ms magenta %d box %s state %s", s.index(),
                    (s.nanos() - t0) / 1e6, s.magenta(), s.box() == null ? "-" : java.util.Arrays.toString(s.box()),
                    s.state()));
        }
        return out.toString();
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
