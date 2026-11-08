package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.FrameAudit;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

final class HandScenario {
    private static final double[] HAND_BOX = {0.5, 0.35, 1.0, 1.0};
    private static final double[] HOTBAR = {0.0, 0.86, 0.76, 1.0};
    private static final int FLIGHT_FRAMES = 40;
    private static final int STILL_FRAMES = 30;
    private static final int NOISE_FRAMES = 30;
    private static final int LIFETIME = 900;
    private static java.nio.file.Path shots;
    private static int maskWidth;

    private HandScenario() {
    }

    static final class Bare extends SettleScenario.SolidScreen {
        Bare() {
            super(SettleScenario.MAGENTA);
        }

        @Override
        public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      float partialTick) {
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean onTop = config.alwaysOnTop;
        int lifetime = config.get(Params.GLASS_LIFETIME);
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        shots = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
        String gameMode = context.computeOnClient(mc -> mc.gameMode.getPlayerMode().getName());
        SettleScenario.command(singleplayer, "gamerule random_tick_speed 0");
        SettleScenario.command(singleplayer, "gamerule advance_time false");
        SettleScenario.command(singleplayer, "gamerule advance_weather false");
        SettleScenario.command(singleplayer, "weather clear");
        SettleScenario.command(singleplayer, "time set 6000");
        SettleScenario.command(singleplayer, "gamemode survival @p");
        SettleScenario.command(singleplayer, "effect give @p minecraft:resistance infinite 255 true");
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
            config.alwaysOnTop = true;
            config.set(Params.GLASS_LIFETIME, LIFETIME);
            prepare(singleplayer, feet);
            McTest.waitForChunksRender(singleplayer);
            context.waitTicks(60);
            for (String item : new String[]{"stone", "air"}) {
                one(context, singleplayer, check, mode, item, feet);
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.GLASS_LIFETIME, lifetime);
            SettleScenario.hold(context, false);
            context.runOnClient(mc -> ShardManager.clear());
            SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(),
                    feet.getZ() - 8, feet.getX() + 8, feet.getY() + 6, feet.getZ() + 12);
            SettleScenario.command(singleplayer, "item replace entity @p weapon.mainhand with minecraft:air");
            SettleScenario.command(singleplayer, "effect clear @p");
            SettleScenario.command(singleplayer, "gamemode %s @p", gameMode);
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            SettleScenario.command(singleplayer, "gamerule random_tick_speed 3");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            SettleScenario.command(singleplayer, "gamerule advance_weather true");
            context.waitTicks(5);
        }
    }

    private static void prepare(TestSingleplayerContext singleplayer, BlockPos feet) {
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(),
                feet.getZ() - 8, feet.getX() + 8, feet.getY() + 6, feet.getZ() + 12);
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:smooth_stone", feet.getX() - 8,
                feet.getY() - 1, feet.getZ() - 8, feet.getX() + 8, feet.getY() - 1, feet.getZ() + 12);
    }

    private static void one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                            BiConsumer<Boolean, String> check, String mode, String item, BlockPos feet) {
        String name = "hand_" + mode + "_" + (item.equals("air") ? "arm" : item);
        context.runOnClient(mc -> ShardManager.clear());
        SettleScenario.hold(context, false);
        SettleScenario.command(singleplayer, "item replace entity @p weapon.mainhand with minecraft:%s", item);
        SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 35);
        context.waitTicks(30);
        int calm = calm(context);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: the scene stopped changing after {} rounds", name, calm);
        List<FullFrame.Image> before = frames(context, 2);

        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(() -> new Bare());
        context.waitTicks(5);
        List<FullFrame.Image> flight = new CopyOnWriteArrayList<>();
        int[] flightDone = {0};
        context.runOnClient(mc -> {
            Mc26.setScreen(null);
            capture(mc, flight, flightDone, FLIGHT_FRAMES);
        });
        waitFor(context, flightDone, FLIGHT_FRAMES);
        SettleScenario.hold(context, true);
        context.runOnClient(mc -> ShardManager.clear());
        context.waitTicks(4);
        boolean[] mask = handMask(context, name);
        int cells = count(mask);
        List<FullFrame.Image> empty = frames(context, NOISE_FRAMES);
        int noise = noise(empty, mask);
        FullFrame.Image reference = empty.isEmpty() ? null : empty.get(0);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: hand mask {} pixels, frame-to-frame noise in it {}", name, cells, noise);
        check.accept(cells > 2000, name + ": the hand must be in the frame (" + cells + " pixels)");
        int drift = before.isEmpty() ? 0 : compare(List.of(before.get(0)), reference, mask, 0)[2];
        int tolerance = Math.max(noise, drift);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: hand before the effect against after it (no pieces in either): largest"
                + " difference {}; tolerance {}", name, drift, tolerance);
        int[] flightResult = compare(flight.subList(Math.min(1, flight.size()), flight.size()), reference, mask, tolerance);
        flightResult[1]++;
        StringBuilder perFrame = new StringBuilder();
        for (int f = 0; f < flight.size(); f++) {
            perFrame.append(' ').append(compare(List.of(flight.get(f)), reference, mask, tolerance)[0]);
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: hand pixels changed per frame in flight:{}", name, perFrame);
        int behind = 0;
        for (FullFrame.Image frame : flight) {
            behind = Math.max(behind, magentaAround(frame, mask));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} frames in flight; hand pixels changed by the pieces: worst frame {}"
                + " (frame {}), largest difference {}; magenta pixels around the hand up to {}", name, flight.size(),
                flightResult[0], flightResult[1], flightResult[2], behind);
        check.accept(flight.size() == FLIGHT_FRAMES, name + ": the frames in flight must arrive (" + flight.size() + ")");
        check.accept(behind > 200, name + ": the pieces in flight must reach the hand (" + behind + " magenta pixels)");
        int magentaHand = 0;
        for (FullFrame.Image frame : flight.subList(Math.min(1, flight.size()), flight.size())) {
            magentaHand = Math.max(magentaHand, magentaIn(frame, mask));
        }
        boolean pack = context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: magenta pixels inside the hand in flight, worst frame: {}", name,
                magentaHand);
        check.accept(magentaHand == 0, name + ": no piece in flight may show inside the hand (" + magentaHand
                + " magenta pixels there)");
        check.accept(flightResult[0] == 0 || pack, name + ": the pieces in flight must stay under the hand ("
                + flightResult[0] + " hand pixels changed in frame " + flightResult[1] + ", largest difference "
                + flightResult[2] + ")");
        for (int f : new int[]{0, 5, 12, 20, 35}) {
            save(flight, f, name + "_flight");
        }

        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(() -> new Bare());
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));
        int still = SettleScenario.waitStill(context, 240);
        SettleScenario.hold(context, false);
        context.waitTicks(SettleScenario.SETTLE_TICKS + 6);
        int settled = SettleScenario.settledCount(context);
        aimAtShards(context, singleplayer, feet, centre(mask));
        context.waitTicks(60);
        FullFrame.request(context, name + "_settled_nohud", false);
        context.runOnClient(mc -> McTest.setHudHidden(mc, true));
        context.waitTicks(3);
        FullFrame.request(context, name + "_settled_nohand", true);
        FullFrame.Image noHand = FullFrame.get(context, name + "_settled_nohand");
        context.runOnClient(mc -> McTest.setHudHidden(mc, false));
        context.waitTicks(3);
        FullFrame.get(context, name + "_settled_nohud");
        boolean[] mask2 = handMask(context, name + "_aimed");
        List<FullFrame.Image> withPieces = frames(context, STILL_FRAMES);
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        context.runOnClient(mc -> ShardManager.clear());
        context.waitTicks(4);
        List<FullFrame.Image> without = frames(context, STILL_FRAMES);
        int noise2 = noise(without, mask2);
        FullFrame.Image clean = without.isEmpty() ? null : without.get(0);
        int hidden = 0;
        if (noHand != null && clean != null) {
            for (int i = 0; i < mask2.length; i++) {
                hidden += mask2[i] && FullFrame.magenta(noHand.argb()[i]) ? 1 : 0;
            }
        }
        int[] settledResult = compare(withPieces, clean, mask2, noise2);
        boolean[] region = piecesRegion(noHand, 16);
        int[] steady = steadiness(withPieces, region);
        int[] steadyEmpty = steadiness(without, region);
        int[] steadyAll = steadiness(withPieces, null);
        int[] steadyAllEmpty = steadiness(without, null);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: whole frame, changed pixels between frames {} with the pieces"
                + " (largest {}), {} without (largest {}); region of the pieces {} pixels", name, steadyAll[0], steadyAll[1],
                steadyAllEmpty[0], steadyAllEmpty[1], count(region));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} shards, {} settled, still after {} ticks; {} magenta pixels behind"
                        + " the hand; hand pixels changed by the settled pieces {} (largest difference {}); {} frames in a row:"
                        + " changed pixels between frames {} with the pieces (largest difference {}), {} without (largest {})",
                name, shards, settled, still, hidden, settledResult[0], settledResult[2], withPieces.size(), steady[0],
                steady[1], steadyEmpty[0], steadyEmpty[1]);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: changes between still frames with the pieces in x {}..{}, y {}..{};"
                + " without in x {}..{}, y {}..{}", name, steady[3], steady[5], steady[4], steady[6], steadyEmpty[3],
                steadyEmpty[5], steadyEmpty[4], steadyEmpty[6]);
        check.accept(hidden > 100, name + ": settled pieces must lie behind the hand (" + hidden + " magenta pixels there)");
        int magentaSettled = 0;
        for (FullFrame.Image frame : withPieces) {
            magentaSettled = Math.max(magentaSettled, magentaIn(frame, mask2));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: magenta pixels inside the hand with settled pieces: {}", name,
                magentaSettled);
        check.accept(magentaSettled == 0, name + ": no settled piece may show inside the hand (" + magentaSettled
                + " magenta pixels there)");
        check.accept(settledResult[0] == 0 || pack, name + ": the settled pieces must stay under the hand ("
                + settledResult[0] + " hand pixels changed, largest difference " + settledResult[2] + ")");
        check.accept(withPieces.size() == STILL_FRAMES && without.size() == STILL_FRAMES,
                name + ": the still frames must arrive (" + withPieces.size() + ", " + without.size() + ")");
        if (steadyEmpty[0] == 0) {
            check.accept(steady[0] == 0, name + ": where the settled pieces lie, a still scene must not change between"
                    + " frames (" + steady[0] + " pixels changed, largest difference " + steady[1] + ")");
        } else {
            ShatterGuiClient.LOGGER.warn("[ShatterTest] {}: where the pieces lie the scene changes between frames without any"
                    + " pieces too ({} pixels), the frame-to-frame check needs a pack without temporal effects", name,
                    steadyEmpty[0]);
        }
        save(withPieces, Math.max(1, steady[2]), name + "_still");
    }

    private static int calm(ClientGameTestContext context) {
        for (int round = 0; round < 20; round++) {
            List<FullFrame.Image> frames = frames(context, 10);
            if (frames.size() == 10 && steadiness(frames, null)[0] == 0) {
                return round;
            }
            context.waitTicks(10);
        }
        return -1;
    }

    private static void save(List<FullFrame.Image> frames, int index, String name) {
        if (index < 0 || index >= frames.size()) {
            return;
        }
        FullFrame.Image image = frames.get(index);
        try {
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(image.width(), image.height(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < image.width(); x++) {
                    int c = image.at(x, y);
                    out.setRGB(x, y, c & 0xFFFFFF);
                }
            }
            java.nio.file.Files.createDirectories(shots);
            javax.imageio.ImageIO.write(out, "png", shots.resolve(name + "_" + index + ".png").toFile());
        } catch (Exception e) {
            ShatterGuiClient.LOGGER.warn("[ShatterTest] could not save {}", name, e);
        }
    }

    private static List<FullFrame.Image> frames(ClientGameTestContext context, int n) {
        List<FullFrame.Image> out = new CopyOnWriteArrayList<>();
        int[] done = {0};
        context.runOnClient(mc -> capture(mc, out, done, n));
        waitFor(context, done, n);
        return new ArrayList<>(out);
    }

    private static void capture(Minecraft mc, List<FullFrame.Image> out, int[] done, int n) {
        int[] taken = {0};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            int index = taken[0]++;
            FullFrame.grab(mc, "hand_series_" + System.identityHashCode(out) + "_" + index, false, image -> {
                while (out.size() < index) {
                    out.add(null);
                }
                if (out.size() == index) {
                    out.add(image);
                } else {
                    out.set(index, image);
                }
                synchronized (done) {
                    done[0]++;
                }
                return Boolean.TRUE;
            });
            if (taken[0] < n) {
                FrameAudit.atFrameEnd(step[0]);
            }
        };
        FrameAudit.atFrameEnd(step[0]);
    }

    private static void waitFor(ClientGameTestContext context, int[] done, int n) {
        for (int i = 0; i < 400; i++) {
            synchronized (done) {
                if (done[0] >= n) {
                    return;
                }
            }
            context.waitTick();
        }
    }

    private static boolean[] handMask(ClientGameTestContext context, String name) {
        FullFrame.request(context, name + "_hand", true);
        FullFrame.Image with = FullFrame.get(context, name + "_hand");
        context.runOnClient(mc -> McTest.setHudHidden(mc, true));
        context.waitTicks(3);
        FullFrame.request(context, name + "_nohand", false);
        FullFrame.Image without = FullFrame.get(context, name + "_nohand");
        context.runOnClient(mc -> McTest.setHudHidden(mc, false));
        context.waitTicks(3);
        if (with == null || without == null) {
            return new boolean[0];
        }
        int w = with.width();
        int h = with.height();
        maskWidth = w;
        boolean[] raw = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double fx = (x + 0.5) / w;
                double fy = (y + 0.5) / h;
                if (!inside(HAND_BOX, fx, fy) || inside(HOTBAR, fx, fy)) {
                    continue;
                }
                raw[y * w + x] = diff(with.at(x, y), without.at(x, y)) > 24;
            }
        }
        boolean[] mask = new boolean[raw.length];
        int r = 3;
        for (int y = r; y < h - r; y++) {
            for (int x = r; x < w - r; x++) {
                boolean all = true;
                for (int dy = -r; dy <= r && all; dy++) {
                    for (int dx = -r; dx <= r && all; dx++) {
                        all = raw[(y + dy) * w + x + dx];
                    }
                }
                mask[y * w + x] = all;
            }
        }
        return mask;
    }

    private static boolean inside(double[] box, double fx, double fy) {
        return fx >= box[0] && fx <= box[2] && fy >= box[1] && fy <= box[3];
    }

    private static int count(boolean[] mask) {
        int n = 0;
        for (boolean m : mask) {
            n += m ? 1 : 0;
        }
        return n;
    }

    private static int diff(int a, int b) {
        return Math.max(Math.abs(FullFrame.r(a) - FullFrame.r(b)), Math.max(Math.abs(FullFrame.g(a) - FullFrame.g(b)),
                Math.abs(FullFrame.b(a) - FullFrame.b(b))));
    }

    private static int noise(List<FullFrame.Image> frames, boolean[] mask) {
        int worst = 0;
        for (int f = 1; f < frames.size(); f++) {
            FullFrame.Image a = frames.get(0);
            FullFrame.Image b = frames.get(f);
            if (a == null || b == null) {
                continue;
            }
            for (int i = 0; i < mask.length; i++) {
                if (mask[i]) {
                    worst = Math.max(worst, diff(a.argb()[i], b.argb()[i]));
                }
            }
        }
        return worst;
    }

    private static int[] compare(List<FullFrame.Image> frames, FullFrame.Image reference, boolean[] mask, int tolerance) {
        int worstCount = reference == null ? Integer.MAX_VALUE : 0;
        int worstFrame = -1;
        int worstDiff = 0;
        if (reference == null) {
            return new int[]{worstCount, worstFrame, worstDiff};
        }
        for (int f = 0; f < frames.size(); f++) {
            FullFrame.Image frame = frames.get(f);
            if (frame == null || frame.argb().length != reference.argb().length) {
                continue;
            }
            int n = 0;
            for (int i = 0; i < mask.length; i++) {
                if (!mask[i]) {
                    continue;
                }
                int d = diff(frame.argb()[i], reference.argb()[i]);
                worstDiff = Math.max(worstDiff, d);
                n += d > tolerance ? 1 : 0;
            }
            if (n > worstCount || worstFrame < 0) {
                worstCount = n;
                worstFrame = f;
            }
        }
        return new int[]{worstCount, worstFrame, worstDiff};
    }

    private static boolean[] piecesRegion(FullFrame.Image frame, int grow) {
        if (frame == null) {
            return new boolean[0];
        }
        int w = frame.width();
        int h = frame.height();
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!FullFrame.magenta(frame.at(x, y))) {
                    continue;
                }
                for (int dy = Math.max(0, y - grow); dy <= Math.min(h - 1, y + grow); dy++) {
                    java.util.Arrays.fill(out, dy * w + Math.max(0, x - grow), dy * w + Math.min(w - 1, x + grow) + 1, true);
                }
            }
        }
        return out;
    }

    private static int magentaIn(FullFrame.Image frame, boolean[] mask) {
        if (frame == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < mask.length && i < frame.argb().length; i++) {
            n += mask[i] && FullFrame.magenta(frame.argb()[i]) ? 1 : 0;
        }
        return n;
    }

    private static int[] steadiness(List<FullFrame.Image> frames, boolean[] region) {
        int changed = 0;
        int worst = 0;
        int worstFrame = 0;
        int most = -1;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int f = 1; f < frames.size(); f++) {
            FullFrame.Image a = frames.get(f - 1);
            FullFrame.Image b = frames.get(f);
            if (a == null || b == null || a.argb().length != b.argb().length) {
                continue;
            }
            int n = 0;
            for (int i = 0; i < a.argb().length; i++) {
                if (region != null && (i >= region.length || !region[i])) {
                    continue;
                }
                int d = diff(a.argb()[i], b.argb()[i]);
                worst = Math.max(worst, d);
                if (d > 0) {
                    n++;
                    minX = Math.min(minX, i % a.width());
                    maxX = Math.max(maxX, i % a.width());
                    minY = Math.min(minY, i / a.width());
                    maxY = Math.max(maxY, i / a.width());
                }
            }
            changed += n;
            if (n > most) {
                most = n;
                worstFrame = f;
            }
        }
        return new int[]{changed, worst, worstFrame, minX, minY, maxX, maxY};
    }

    private static int magentaAround(FullFrame.Image frame, boolean[] mask) {
        if (frame == null) {
            return 0;
        }
        int n = 0;
        int w = frame.width();
        int h = frame.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double fx = (x + 0.5) / w;
                double fy = (y + 0.5) / h;
                int i = y * w + x;
                if (inside(HAND_BOX, fx, fy) && !inside(HOTBAR, fx, fy) && !mask[i] && FullFrame.magenta(frame.argb()[i])) {
                    n++;
                }
            }
        }
        return n;
    }

    private static double[] centre(boolean[] mask) {
        int w = maskWidth;
        if (w == 0 || mask.length == 0) {
            return new double[]{0.8, 0.8};
        }
        int h = mask.length / w;
        double sx = 0;
        double sy = 0;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y * w + x]) {
                    sx += x + 0.5;
                    sy += y + 0.5;
                    n++;
                }
            }
        }
        return n == 0 ? new double[]{0.8, 0.8} : new double[]{sx / n / w, sy / n / h};
    }

    private static void aimAtShards(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos feet,
                                    double[] target) {
        double[] aim = context.computeOnClient(mc -> {
            Vector3d mean = new Vector3d();
            List<Vector3d> all = new ArrayList<>();
            for (ShardManager.Batch batch : ShardManager.batches()) {
                for (ShardInstance shard : batch.shards()) {
                    all.add(new Vector3d(shard.pos().x, shard.pos().y, shard.pos().z));
                    mean.add(all.get(all.size() - 1));
                }
            }
            if (all.isEmpty()) {
                return null;
            }
            mean.div(all.size());
            Vector3d centre = all.get(0);
            int dense = -1;
            for (Vector3d p : all) {
                int near = 0;
                for (Vector3d q : all) {
                    near += p.distanceSquared(q) < 0.25 ? 1 : 0;
                }
                if (near > dense) {
                    dense = near;
                    centre = p;
                }
            }
            Matrix4f projection = new Matrix4f(McTest.renderState(mc).levelRenderState.cameraRenderState.projectionMatrix);
            double eyeX = feet.getX() + 0.5;
            double eyeY = feet.getY() + mc.player.getEyeHeight();
            double eyeZ = feet.getZ() + 0.5;
            Vector3d to = new Vector3d(centre.x - eyeX, centre.y - eyeY, centre.z - eyeZ).normalize();
            double cx = (target[0] * 2 - 1) / projection.m00();
            double cy = (1 - target[1] * 2) / projection.m11();
            double best = Double.MAX_VALUE;
            double bestYaw = 0;
            double bestPitch = 0;
            for (double step : new double[]{1.0, 0.1, 0.01}) {
                double y0 = step == 1.0 ? -180 : bestYaw - step * 10;
                double y1 = step == 1.0 ? 180 : bestYaw + step * 10;
                double p0 = step == 1.0 ? -89 : bestPitch - step * 10;
                double p1 = step == 1.0 ? 89 : bestPitch + step * 10;
                for (double yaw = y0; yaw <= y1; yaw += step) {
                    for (double pitch = p0; pitch <= p1; pitch += step) {
                        double ry = Math.toRadians(yaw);
                        double rp = Math.toRadians(pitch);
                        Vector3d f = new Vector3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
                        Vector3d r = new Vector3d(-Math.cos(ry), 0, -Math.sin(ry));
                        Vector3d u = new Vector3d(r).cross(f);
                        Vector3d d = new Vector3d(r).mul(cx).add(new Vector3d(u).mul(cy)).add(f).normalize();
                        double angle = d.angle(to);
                        if (angle < best) {
                            best = angle;
                            bestYaw = yaw;
                            bestPitch = pitch;
                        }
                    }
                }
            }
            return new double[]{bestYaw, bestPitch};
        });
        if (aim != null) {
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), (float) aim[0],
                    (float) Math.clamp(aim[1], -90, 90));
        }
    }
}
