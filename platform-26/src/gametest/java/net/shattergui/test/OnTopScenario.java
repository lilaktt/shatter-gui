package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Effects over blocks ({@code -PtestOnly=ontop}; also with {@code -PspatialGui=1.8} and {@code -Pshaders}): the player
 * stands under a block, a chest is opened, then a stone wall is put between the player and the chest and the chest is
 * closed with fly — the pieces fly through the wall into the chest. Frames of the flight are compared with the world
 * after the effect over the wall (the share of it that shows pieces). Once the pieces are behind the wall: with
 * {@code alwaysOnTop} clearly more of them shows than without it at the same tick, and in the last frame the wall hides
 * them completely without it (which shows the check sees clipping) but not with it. Screenshots
 * {@code ontop_<mode>_<on|off>_<effect>_t<tick>} for looking at; glass is only photographed.
 */
final class OnTopScenario {
    /** Ticks of the fly-back for this test: long enough to catch frames behind the wall. */
    private static final int FLY_TICKS = 20;
    /**
     * Exact ticks of the frames. The last is t16, not t18: at t18 the whole interface is 90x85 pixels, 0.006 of the wall,
     * less than {@link #LAST_VISIBLE}, and it only passed while the shots drifted to earlier moments of the flight.
     */
    private static final int[] SHOTS = {12, 15, 16};
    /** The wall above the HUD (with Spatial GUI in the third person the plane flies in from the side). */
    private static final double[] WALL = {0.05, 0.05, 0.95, 0.8};
    /**
     * On top, at least this much more of the wall shows pieces than without, and in the last frame (the plane has shrunk
     * to a small square near the chest by then) at least {@link #LAST_VISIBLE}.
     */
    private static final double MORE = 0.01;
    private static final double LAST_VISIBLE = 0.008;
    /** The player's hand (bottom right, first person) and how much of it a shard may change (edge pixels). */
    private static final double[] HAND = {0.72, 0.78, 0.84, 0.97};
    private static final double HAND_COVERED = 0.03;
    /** Without on top, at most this much of the wall shows pieces in the last frame. */
    private static final double HIDDEN = 0.005;
    /** Seed of the pieces' random numbers in the scenarios that check where pieces go. */
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
        // The ground under the wall, as it was: grass under a stone wall dies on some random tick, and how long the wall
        // stood depended on how long the glass pieces lived, so a dirt patch was left there in some runs and not in others
        // (and showed in every later frame). The wall now goes with the ground restored, which is checked.
        BlockPos[] ground = {wall.offset(-1, -1, 0), wall.offset(0, -1, 0), wall.offset(1, -1, 0)};
        net.minecraft.world.level.block.state.BlockState[] groundBefore = singleplayer.getServer().computeOnServer(
                server -> java.util.Arrays.stream(ground).map(p -> server.overworld().getBlockState(p))
                        .toArray(net.minecraft.world.level.block.state.BlockState[]::new));
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.set(Params.FLY_DURATION, FLY_TICKS);
            // The player stands under a block; the chest is in front.
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

    /** Puts the ground back as it was (grass under the wall may have died) and random ticks back to normal. */
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

    /** The fly frames behind the wall: on top, more of them shows than without; without, the last is hidden. */
    private static void compare(BiConsumer<Boolean, String> check, String name, double[] on, double[] off) {
        if (on == null || off == null) {
            check.accept(false, name + ": the fly frames must arrive");
            return;
        }
        // The first frame may still be in front of the wall's face.
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

    /** Shares of the wall that show pieces in the frames of {@link #SHOTS} (fly), or null (glass). */
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
        context.waitFor(mc -> mc.screen instanceof AbstractContainerScreen<?>, 40);
        context.waitTicks(10);
        // A wall between the player and the open chest: the pieces fly through it.
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", wall.getX() - 1, wall.getY(), wall.getZ(),
                wall.getX() + 1, wall.getY() + 1, wall.getZ());
        // Many random ticks while the wall stands: the grass under it dies in every run, not in some (the patch the
        // ground check catches if the wall goes without putting the ground back).
        command(singleplayer, "gamerule random_tick_speed 300");
        context.waitTicks(3);
        // The glass pieces fly the same way in every run.
        context.runOnClient(mc -> ShardManager.seedForTests(TEST_SEED));
        context.runOnClient(mc -> mc.setScreen(null));
        // Every frame is requested on its exact tick and collected afterwards: waiting for one in between (a read-back
        // takes one to a few ticks) moved the later shots by a varying number of ticks, and the last one sometimes
        // caught the pieces already at the chest (ontop_flat t18 failed now and then).
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
            // Glass shards fly down past the player's hand: the hand stays on top (the same as after the effect).
            // Only the cells that are the hand (it differs there from the same view without it), one cell in from its
            // outline: the box around the hand also holds sky and ground, where pieces falling past it are right to show,
            // and outline cells are partly background. Measured over that box the share depended on how the pieces fell
            // (ontop_spatial_on_glass failed now and then at 0.034 of 0.03).
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
            boolean irisHand = context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse());
            check.accept(hand <= HAND_COVERED || irisHand, name + ": the shards must stay under the player's hand, "
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

    /**
     * Cells (of the {@link FrameGrab} grid) where the player's hand is: the frame differs from the same frame with the
     * hand and the HUD hidden. Eroded by one cell. Empty in the third person (no hand).
     */
    private static boolean[] handMask(ClientGameTestContext context, String name) {
        FrameGrab.request(context, name + "_hand", false);
        FrameGrab.Frame with = FrameGrab.get(context, name + "_hand");
        context.runOnClient(mc -> mc.options.hideGui = true);
        context.waitTicks(2);
        FrameGrab.request(context, name + "_nohand", false);
        FrameGrab.Frame without = FrameGrab.get(context, name + "_nohand");
        context.runOnClient(mc -> mc.options.hideGui = false);
        context.waitTicks(2);
        boolean[] raw = new boolean[FrameGrab.W * FrameGrab.H];
        if (with == null || without == null) {
            return raw;
        }
        for (int y = 0; y < FrameGrab.H; y++) {
            for (int x = 0; x < FrameGrab.W; x++) {
                double fx = (x + 0.5) / FrameGrab.W;
                double fy = (y + 0.5) / FrameGrab.H;
                // The hand's corner of the frame only: hiding the HUD also takes the hotbar and the crosshair away.
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

    /** Share of the masked cells whose colour differs by more than {@code threshold} in some channel (0 if none). */
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
