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
    private static final int[] SHOTS = {12, 15, 18};
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
                    double[] s = one(context, singleplayer, check, mode, on, fx, wall);
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
            context.waitTicks(5);
        }
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
                                BiConsumer<Boolean, String> check, String mode, boolean on, String fx, BlockPos wall) {
        ShatterConfig config = ShatterConfig.get();
        String name = "ontop_" + mode + "_" + (on ? "on" : "off") + "_" + fx;
        config.alwaysOnTop = on;
        config.effect = fx;
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", wall.getX() - 1, wall.getY(), wall.getZ(),
                wall.getX() + 1, wall.getY() + 1, wall.getZ());
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(mc -> mc.screen instanceof AbstractContainerScreen<?>, 40);
        context.waitTicks(10);
        // A wall between the player and the open chest: the pieces fly through it.
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", wall.getX() - 1, wall.getY(), wall.getZ(),
                wall.getX() + 1, wall.getY() + 1, wall.getZ());
        context.waitTicks(3);
        context.runOnClient(mc -> mc.setScreen(null));
        FrameGrab.Frame[] frames = new FrameGrab.Frame[SHOTS.length];
        int tick = 0;
        for (int i = 0; i < SHOTS.length; i++) {
            context.waitTicks(SHOTS[i] - tick);
            tick = SHOTS[i];
            FrameGrab.request(context, name + "_t" + SHOTS[i], true);
            frames[i] = FrameGrab.get(context, name + "_t" + SHOTS[i]);
            tick += 2;
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
            double hand = 0;
            for (FrameGrab.Frame frame : frames) {
                hand = Math.max(hand, frame == null ? 0 : FrameGrab.changed(frame, after, 24, HAND));
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: share of the hand covered by shards {}", name,
                    String.format(Locale.ROOT, "%.3f", hand));
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

    private static void command(TestSingleplayerContext singleplayer, String format, Object... args) {
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, format, args));
    }
}
