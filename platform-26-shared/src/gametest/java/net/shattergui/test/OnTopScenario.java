package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;

import java.util.Locale;
import java.util.function.BiConsumer;

final class OnTopScenario {
    private static final int FLY_TICKS = 20;
    private static final int[] SHOTS = {12, 15, 16};
    private static final double[] WALL = {0.05, 0.05, 0.95, 0.8};
    private static final double MORE = 0.01;
    private static final double LAST_VISIBLE = 0.008;
    private static final double[] HAND = {0.72, 0.78, 0.84, 0.97};
    private static final double HAND_COVERED = 0.03;
    private static final double HIDDEN = 0.005;
    static final long TEST_SEED = 20261103L;

    private OnTopScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean onTop = config.alwaysOnTop;
        int fly = config.get(Params.FLY_DURATION);
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        BlockPos chest = feet.offset(0, 0, 3);
        BlockPos wall = feet.offset(0, 0, 2);
        BlockPos[] ground = {wall.offset(-1, -1, 0), wall.offset(0, -1, 0), wall.offset(1, -1, 0)};
        net.minecraft.world.level.block.state.BlockState[] groundBefore = singleplayer.getServer().computeOnServer(
                server -> java.util.Arrays.stream(ground).map(p -> server.overworld().getBlockState(p))
                        .toArray(net.minecraft.world.level.block.state.BlockState[]::new));
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.set(Params.FLY_DURATION, FLY_TICKS);
            command(singleplayer, "setblock %d %d %d minecraft:stone", feet.getX(), feet.getY() + 2, feet.getZ());
            command(singleplayer, "setblock %d %d %d minecraft:chest[facing=north]", chest.getX(), chest.getY(), chest.getZ());
            FlyScenario.lookAt(context, singleplayer, feet, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            double[][] shares = new double[2][];
            for (boolean on : new boolean[]{true, false}) {
                for (String fx : new String[]{"fly", "glass"}) {
                    double[] s = one(context, singleplayer, check, mode, on, fx, wall, ground, groundBefore);
                    if (s != null) {
                        shares[on ? 0 : 1] = s;
                    }
                }
            }
            compare(check, "ontop_" + mode, shares[0], shares[1]);
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.FLY_DURATION, fly);
            command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", wall.getX() - 1, wall.getY(), wall.getZ(),
                    wall.getX() + 1, wall.getY() + 1, chest.getZ());
            command(singleplayer, "setblock %d %d %d minecraft:air", feet.getX(), feet.getY() + 2, feet.getZ());
            restoreGround(singleplayer, ground, groundBefore);
            context.waitTicks(5);
        }
        java.util.List<String> changed = singleplayer.getServer().computeOnServer(server -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (int i = 0; i < ground.length; i++) {
                if (server.overworld().getBlockState(ground[i]) != groundBefore[i]) {
                    out.add(ground[i].toShortString() + ": " + server.overworld().getBlockState(ground[i]));
                }
            }
            return out;
        });
        check.accept(changed.isEmpty(), "ontop_" + mode + ": the ground under the wall must be as before " + changed);
    }

    private static void restoreGround(TestSingleplayerContext singleplayer, BlockPos[] ground,
                                      net.minecraft.world.level.block.state.BlockState[] before) {
        command(singleplayer, "gamerule random_tick_speed 3");
        singleplayer.getServer().runOnServer(server -> {
            for (int i = 0; i < ground.length; i++) {
                if (server.overworld().getBlockState(ground[i]) != before[i]) {
                    ShatterGuiClient.LOGGER.info("[ShatterTest] ontop: ground {} under the wall turned into {}, put back",
                            ground[i].toShortString(), server.overworld().getBlockState(ground[i]));
                    server.overworld().setBlockAndUpdate(ground[i], before[i]);
                }
            }
        });
    }

    private static void compare(BiConsumer<Boolean, String> check, String name, double[] on, double[] off) {
        if (on == null || off == null) {
            check.accept(false, name + ": the fly frames must arrive");
            return;
        }
        for (int i = 1; i < SHOTS.length; i++) {
            check.accept(on[i] >= off[i] + MORE, String.format(Locale.ROOT, "%s t%d: with alwaysOnTop the pieces must "
                    + "show over the wall (%.3f of it, without: %.3f)", name, SHOTS[i], on[i], off[i]));
        }
        int last = SHOTS.length - 1;
        check.accept(on[last] >= LAST_VISIBLE, String.format(Locale.ROOT, "%s: the last frame must still show the pieces over the "
                + "wall with alwaysOnTop (%.3f)", name, on[last]));
        check.accept(off[last] <= HIDDEN, String.format(Locale.ROOT, "%s: without alwaysOnTop the wall must hide the "
                + "pieces behind it in the last frame (%.3f)", name, off[last]));
    }

    private static double[] one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                                BiConsumer<Boolean, String> check, String mode, boolean on, String fx, BlockPos wall,
                                BlockPos[] ground, net.minecraft.world.level.block.state.BlockState[] groundBefore) {
        ShatterConfig config = ShatterConfig.get();
        String name = "ontop_" + mode + "_" + (on ? "on" : "off") + "_" + fx;
        config.alwaysOnTop = on;
        config.effect = fx;
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", wall.getX() - 1, wall.getY(), wall.getZ(),
                wall.getX() + 1, wall.getY() + 1, wall.getZ());
        restoreGround(singleplayer, ground, groundBefore);
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(mc -> Mc26.screen() instanceof AbstractContainerScreen<?>, 40);
        context.waitTicks(10);
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", wall.getX() - 1, wall.getY(), wall.getZ(),
                wall.getX() + 1, wall.getY() + 1, wall.getZ());
        command(singleplayer, "gamerule random_tick_speed 300");
        context.waitTicks(3);
        context.runOnClient(mc -> ShardManager.seedForTests(TEST_SEED));
        context.runOnClient(mc -> Mc26.setScreen(null));
        FrameGrab.Frame[] frames = new FrameGrab.Frame[SHOTS.length];
        int tick = 0;
        for (int i = 0; i < SHOTS.length; i++) {
            context.waitTicks(SHOTS[i] - tick);
            tick = SHOTS[i];
            FrameGrab.request(context, name + "_t" + SHOTS[i], true);
        }
        for (int i = 0; i < SHOTS.length; i++) {
            frames[i] = FrameGrab.get(context, name + "_t" + SHOTS[i]);
        }
        for (int i = 0; i < 600 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
        context.waitTicks(5);
        FrameGrab.request(context, name + "_after", true);
        FrameGrab.Frame after = FrameGrab.get(context, name + "_after");
        if (after == null) {
            check.accept(false, name + ": the frame after the effect must arrive");
            return null;
        }
        if (!"fly".equals(fx)) {
            boolean[] mask = handMask(context, name);
            int cells = 0;
            for (boolean m : mask) {
                cells += m ? 1 : 0;
            }
            double hand = 0;
            for (FrameGrab.Frame frame : frames) {
                hand = Math.max(hand, frame == null ? 0 : changedIn(frame, after, 24, mask));
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: share of the hand covered by shards {} ({} hand cells)", name,
                    String.format(Locale.ROOT, "%.3f", hand), cells);
            check.accept(hand <= HAND_COVERED, name + ": the shards must stay under the player's hand, "
                    + String.format(Locale.ROOT, "%.3f", hand) + " of it changed");
            return null;
        }
        double[] shares = new double[frames.length];
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < frames.length; i++) {
            shares[i] = frames[i] == null ? 0 : FrameGrab.changed(frames[i], after, 24, WALL);
            log.append(String.format(Locale.ROOT, " t%d=%.3f", SHOTS[i], shares[i]));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: share of the wall showing pieces:{}", name, log);
        return shares;
    }

    private static boolean[] handMask(ClientGameTestContext context, String name) {
        FrameGrab.request(context, name + "_hand", false);
        FrameGrab.Frame with = FrameGrab.get(context, name + "_hand");
        context.runOnClient(mc -> McTest.setHudHidden(mc, true));
        context.waitTicks(2);
        FrameGrab.request(context, name + "_nohand", false);
        FrameGrab.Frame without = FrameGrab.get(context, name + "_nohand");
        context.runOnClient(mc -> McTest.setHudHidden(mc, false));
        context.waitTicks(2);
        boolean[] raw = new boolean[FrameGrab.W * FrameGrab.H];
        if (with == null || without == null) {
            return raw;
        }
        for (int y = 0; y < FrameGrab.H; y++) {
            for (int x = 0; x < FrameGrab.W; x++) {
                double fx = (x + 0.5) / FrameGrab.W;
                double fy = (y + 0.5) / FrameGrab.H;
                if (fx < HAND[0] - 0.1 || fx > 1 || fy < HAND[1] - 0.15 || fy > HAND[3]) {
                    continue;
                }
                int o = (y * FrameGrab.W + x) * 3;
                int d = Math.max(Math.abs(with.rgb()[o] - without.rgb()[o]), Math.max(
                        Math.abs(with.rgb()[o + 1] - without.rgb()[o + 1]), Math.abs(with.rgb()[o + 2] - without.rgb()[o + 2])));
                raw[y * FrameGrab.W + x] = d > 24;
            }
        }
        boolean[] mask = new boolean[raw.length];
        for (int y = 1; y < FrameGrab.H - 1; y++) {
            for (int x = 1; x < FrameGrab.W - 1; x++) {
                boolean all = true;
                for (int dy = -1; dy <= 1 && all; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        all &= raw[(y + dy) * FrameGrab.W + x + dx];
                    }
                }
                mask[y * FrameGrab.W + x] = all;
            }
        }
        return mask;
    }

    private static double changedIn(FrameGrab.Frame a, FrameGrab.Frame b, int threshold, boolean[] mask) {
        int count = 0;
        int cells = 0;
        for (int i = 0; i < mask.length; i++) {
            if (!mask[i]) {
                continue;
            }
            cells++;
            int o = i * 3;
            int d = Math.max(Math.abs(a.rgb()[o] - b.rgb()[o]), Math.max(Math.abs(a.rgb()[o + 1] - b.rgb()[o + 1]),
                    Math.abs(a.rgb()[o + 2] - b.rgb()[o + 2])));
            count += d > threshold ? 1 : 0;
        }
        return cells == 0 ? 0 : count / (double) cells;
    }

    private static void command(TestSingleplayerContext singleplayer, String format, Object... args) {
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, format, args));
    }
}
