package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

final class ModelMaskScenario {
    private static final double[] VIEW = {0.0, 0.0, 1.0, 0.8};
    private static final int PIECE_FRAMES = 16;
    private static final int EXTRA_TOLERANCE = 10;
    private static final int REACH_GROW = 8;
    private static final int MASK_EROSION = 4;

    private enum View {
        FIRST("first", CameraType.FIRST_PERSON, 180f),
        THIRD_BACK("third_back", CameraType.THIRD_PERSON_BACK, 180f),
        THIRD_FRONT("third_front", CameraType.THIRD_PERSON_FRONT, 0f);

        final String tag;
        final CameraType camera;
        final float yaw;

        View(String tag, CameraType camera, float yaw) {
            this.tag = tag;
            this.camera = camera;
            this.yaw = yaw;
        }
    }

    private ModelMaskScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean onTop = config.alwaysOnTop;
        float burst = config.get(Params.GLASS_BURST);
        float gravity = config.get(Params.GLASS_GRAVITY);
        float spin = config.get(Params.GLASS_SPIN);
        int lifetime = config.get(Params.GLASS_LIFETIME);
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        Path shots = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
        SettleScenario.command(singleplayer, "gamerule random_tick_speed 0");
        SettleScenario.command(singleplayer, "gamerule advance_time false");
        SettleScenario.command(singleplayer, "gamerule advance_weather false");
        SettleScenario.command(singleplayer, "weather clear");
        SettleScenario.command(singleplayer, "time set 6000");
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 7, feet.getY(),
                feet.getZ() - 16, feet.getX() + 7, feet.getY() + 6, feet.getZ() + 6);
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", feet.getX() - 7, feet.getY() - 1,
                feet.getZ() - 16, feet.getX() + 7, feet.getY() - 1, feet.getZ() + 6);
        try {
            config.effect = "glass";
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.alwaysOnTop = true;
            config.set(Params.GLASS_BURST, 0);
            config.set(Params.GLASS_GRAVITY, 0);
            config.set(Params.GLASS_SPIN, 0);
            config.set(Params.GLASS_LIFETIME, 400);
            boolean pack = context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse());
            for (View view : View.values()) {
                for (boolean behind : new boolean[]{true, false}) {
                    one(context, singleplayer, check, "mask_" + mode + "_" + view.tag + (behind ? "_behind" : "_front"),
                            view, behind, feet, pack, shots);
                }
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.GLASS_BURST, burst);
            config.set(Params.GLASS_GRAVITY, gravity);
            config.set(Params.GLASS_SPIN, spin);
            config.set(Params.GLASS_LIFETIME, lifetime);
            context.runOnClient(mc -> ShardManager.clear());
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            SettleScenario.command(singleplayer, "kill @e[type=minecraft:armor_stand]");
            SettleScenario.command(singleplayer, "effect clear @p");
            SettleScenario.command(singleplayer, "gamerule random_tick_speed 3");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            SettleScenario.command(singleplayer, "gamerule advance_weather true");
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            context.waitTicks(5);
        }
    }

    private static void tpExact(TestSingleplayerContext singleplayer, double x, double y, double z, float yaw) {
        SettleScenario.command(singleplayer, "tp @p %.3f %.3f %.3f %.3f 0", x, y, z, yaw);
    }

    private static void stand(TestSingleplayerContext singleplayer, double x, double y, double z) {
        SettleScenario.command(singleplayer, "kill @e[type=minecraft:armor_stand]");
        SettleScenario.command(singleplayer, "summon minecraft:armor_stand %.3f %.3f %.3f {NoGravity:1b,Invulnerable:1b,"
                + "ShowArms:1b,Rotation:[0f,0f]}", x, y, z);
    }

    private static void modelVisible(TestSingleplayerContext singleplayer, View view, boolean visible) {
        if (view == View.FIRST) {
            SettleScenario.command(singleplayer, "data merge entity @e[type=minecraft:armor_stand,limit=1] {Invisible:%s}",
                    visible ? "0b" : "1b");
        } else if (visible) {
            SettleScenario.command(singleplayer, "effect clear @p minecraft:invisibility");
        } else {
            SettleScenario.command(singleplayer, "effect give @p minecraft:invisibility infinite 0 true");
        }
    }

    private static void one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                            BiConsumer<Boolean, String> check, String name, View view, boolean behind, BlockPos feet,
                            boolean pack, Path shots) {
        double x = feet.getX() + 0.5;
        double y = feet.getY();
        double plane = feet.getZ() - 6 + 0.5;
        double model = plane + (behind ? 1.5 : -1.5);
        double camera = model + 4.0;
        context.runOnClient(mc -> ShardManager.clear());
        SettleScenario.command(singleplayer, "kill @e[type=minecraft:armor_stand]");
        SettleScenario.command(singleplayer, "effect clear @p");

        Runnable place = () -> {
            if (view == View.FIRST) {
                stand(singleplayer, x, y, model);
                tpExact(singleplayer, x, y, camera, view.yaw);
            } else {
                tpExact(singleplayer, x, y, model, view.yaw);
            }
            context.runOnClient(mc -> mc.options.setCameraType(view.camera));
        };
        place.run();
        context.waitTicks(30);
        FullFrame.Image with = grab(context, name + "_model");
        modelVisible(singleplayer, view, false);
        context.waitTicks(6);
        FullFrame.Image without = grab(context, name + "_nomodel");
        modelVisible(singleplayer, view, true);
        context.waitTicks(6);
        boolean[] mask = mask(with, without);
        int cells = count(mask);

        SettleScenario.command(singleplayer, "kill @e[type=minecraft:armor_stand]");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        tpExact(singleplayer, x, y, plane + 1.0, 180f);
        context.waitTicks(10);
        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(HandScenario.Bare::new);
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTicks(3);
        place.run();
        context.waitTicks(30);
        int live = context.computeOnClient(mc -> ShardManager.liveShardCount());
        int hidden = context.computeOnClient(mc -> {
            int n = 0;
            for (ShardManager.Batch batch : ShardManager.batches()) {
                for (net.shattergui.shatter.ShardInstance shard : batch.shards()) {
                    n += shard.hidden() ? 1 : 0;
                }
            }
            return n;
        });
        int changedWorst = 0;
        int magentaWorst = 0;
        int magentaFrame = 0;
        FullFrame.Image sample = null;
        boolean[] changedAt = new boolean[mask.length];
        int[] timesChanged = new int[mask.length];
        int pairsUsed = 0;
        int elsewhere = 0;
        List<FullFrame.Image[]> series = pairs(context, PIECE_FRAMES);
        int[] flicker = new int[mask.length];
        for (int k = 1; k < series.size(); k++) {
            FullFrame.Image a = series.get(k - 1)[1];
            FullFrame.Image b = series.get(k)[1];
            if (a == null || b == null || a.argb().length != mask.length || b.argb().length != mask.length) {
                continue;
            }
            for (int p = 0; p < mask.length; p++) {
                if (mask[p]) {
                    flicker[p] = Math.max(flicker[p], diff(a.argb()[p], b.argb()[p]));
                }
            }
        }
        int flickering = 0;
        for (int p = 0; p < mask.length; p++) {
            flickering += mask[p] && flicker[p] > 0 ? 1 : 0;
        }
        for (FullFrame.Image[] pair : series) {
            FullFrame.Image frame = pair[0];
            FullFrame.Image bare = pair[1];
            if (frame == null || bare == null || frame.argb().length != mask.length) {
                continue;
            }
            sample = sample == null ? frame : sample;
            int changed = 0;
            int magenta = 0;
            int[] reach = magentaBox(frame, REACH_GROW);
            for (int p = 0; p < mask.length; p++) {
                if (!mask[p]) {
                    continue;
                }
                if (diff(frame.argb()[p], bare.argb()[p]) > flicker[p] + EXTRA_TOLERANCE) {
                    int px = p % frame.width();
                    int py = p / frame.width();
                    if (reach != null && px >= reach[0] && px < reach[2] && py >= reach[1] && py < reach[3]) {
                        changed++;
                        timesChanged[p]++;
                    } else {
                        elsewhere++;
                    }
                }
                if (FullFrame.magenta(frame.argb()[p])) {
                    magenta++;
                }
            }
            changedWorst = Math.max(changedWorst, changed);
            pairsUsed++;
            magentaWorst = Math.max(magentaWorst, magenta);
            magentaFrame = Math.max(magentaFrame, FullFrame.blob(frame, FullFrame::magenta, VIEW).count());
        }
        int covered = 0;
        for (int p = 0; p < mask.length; p++) {
            if (pairsUsed > 0 && timesChanged[p] * 4 >= pairsUsed * 3) {
                changedAt[p] = true;
                covered++;
            }
        }
        context.runOnClient(mc -> ShardManager.clear());
        if (sample != null) {
            saveMask(shots, name, sample, mask, changedAt);
        }
        String text = String.format(Locale.ROOT, "%s: model mask %d px, %d pieces alive (%d drawn with depth); with pieces "
                        + "vs the next frame without them, %d pairs: %d mask px changed in at least 3 of 4 pairs (covered by a "
                        + "piece), worst single pair %d px where the pieces reach and %d px elsewhere in all pairs; %d mask px "
                        + "change by themselves between frames without pieces (the model's arms sway); worst frame %d magenta px "
                        + "in the mask, %d in the frame%s", name, cells, live, hidden, pairsUsed, covered, changedWorst,
                elsewhere, flickering, magentaWorst, magentaFrame,
                pack ? " (shader pack: the pack changes the image by itself, magenta counts)" : "");
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}", text);
        check.accept(cells > 800, name + ": the model must be on screen to make the mask (" + cells + " px)");
        check.accept(live > 0, name + ": the pieces must be alive (" + live + ")");
        check.accept(magentaFrame > 2000, name + ": the pieces must be on screen (" + magentaFrame + " magenta px)");
        if (behind) {
            check.accept(magentaWorst == 0, name + ": a piece behind the model must not show over it: " + text);
            if (!pack) {
                check.accept(covered == 0, name + ": a piece behind the model must not change a pixel of it: " + text);
            }
        } else {
            check.accept(magentaWorst > 200, name + ": a piece in front of the model must be seen over it: " + text);
        }
        SettleScenario.command(singleplayer, "kill @e[type=minecraft:armor_stand]");
        context.waitTicks(3);
    }

    private static FullFrame.Image grab(ClientGameTestContext context, String name) {
        String key = name != null ? name : "mask_frame_" + System.nanoTime();
        FullFrame.request(context, key, name != null);
        return FullFrame.get(context, key);
    }

    private static boolean[] mask(FullFrame.Image with, FullFrame.Image without) {
        if (with == null || without == null) {
            return new boolean[0];
        }
        int w = with.width();
        int h = with.height();
        boolean[] raw = new boolean[w * h];
        for (int py = 0; py < h * VIEW[3]; py++) {
            for (int px = 0; px < w; px++) {
                raw[py * w + px] = diff(with.at(px, py), without.at(px, py)) > 24;
            }
        }
        boolean[] mask = new boolean[raw.length];
        int r = MASK_EROSION;
        for (int py = r; py < h - r; py++) {
            for (int px = r; px < w - r; px++) {
                boolean all = true;
                for (int dy = -r; dy <= r && all; dy++) {
                    for (int dx = -r; dx <= r && all; dx++) {
                        all = raw[(py + dy) * w + px + dx];
                    }
                }
                mask[py * w + px] = all;
            }
        }
        return mask;
    }

    private static int[] magentaBox(FullFrame.Image frame, int grow) {
        int x0 = frame.width();
        int y0 = frame.height();
        int x1 = -1;
        int y1 = -1;
        for (int py = 0; py < frame.height() * VIEW[3]; py++) {
            for (int px = 0; px < frame.width(); px++) {
                if (FullFrame.magenta(frame.at(px, py))) {
                    x0 = Math.min(x0, px);
                    y0 = Math.min(y0, py);
                    x1 = Math.max(x1, px + 1);
                    y1 = Math.max(y1, py + 1);
                }
            }
        }
        return x1 < 0 ? null : new int[]{x0 - grow, y0 - grow, x1 + grow, y1 + grow};
    }

    private static List<FullFrame.Image[]> pairs(ClientGameTestContext context, int n) {
        FullFrame.Image[][] out = new FullFrame.Image[n][2];
        int[] done = {0};
        context.runOnClient(mc -> {
            int[] taken = {0};
            Runnable[] step = new Runnable[1];
            step[0] = () -> {
                int index = taken[0]++;
                int pair = index / 2;
                int side = index % 2;
                FullFrame.grab(mc, "mask_pair_" + System.identityHashCode(out) + "_" + index, false, image -> {
                    out[pair][side] = image;
                    synchronized (done) {
                        done[0]++;
                    }
                    return Boolean.TRUE;
                });
                net.shattergui.render.ShardRenderer.suppressForTests(side == 0);
                if (taken[0] < 2 * n) {
                    net.shattergui.debug.FrameAudit.atFrameEnd(step[0]);
                } else {
                    net.shattergui.render.ShardRenderer.suppressForTests(false);
                }
            };
            net.shattergui.debug.FrameAudit.atFrameEnd(step[0]);
        });
        for (int i = 0; i < 400; i++) {
            synchronized (done) {
                if (done[0] >= 2 * n) {
                    break;
                }
            }
            context.waitTick();
        }
        context.runOnClient(mc -> net.shattergui.render.ShardRenderer.suppressForTests(false));
        return java.util.Arrays.asList(out);
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

    private static void saveMask(Path shots, String name, FullFrame.Image frame, boolean[] mask, boolean[] changedAt) {
        try (com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(frame.width(),
                frame.height(), false)) {
            for (int py = 0; py < frame.height(); py++) {
                for (int px = 0; px < frame.width(); px++) {
                    int c = frame.at(px, py);
                    int p = py * frame.width() + px;
                    if (changedAt.length > p && changedAt[p]) {
                        c = 0xFFFF0000;
                    } else if (mask.length > 0 && mask[p]) {
                        c = (c & 0xFFFEFEFE) >> 1 | 0xFF00A000;
                    }
                    image.setPixel(px, py, 0xFF000000 | c);
                }
            }
            Files.createDirectories(shots);
            image.writeToFile(shots.resolve(name + "_mask.png"));
        } catch (Exception e) {
            ShatterGuiClient.LOGGER.warn("[ShatterTest] could not save the mask of {}", name, e);
        }
    }
}
