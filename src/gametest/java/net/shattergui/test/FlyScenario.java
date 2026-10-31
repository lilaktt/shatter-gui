package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.fly.FlyOpen;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fly-in and fly-back with a real chest and a chest minecart opened by the use key (right click), as a player would:
 * screenshots of several moments of both flights, plus checks that the open animation starts only with a source,
 * blocks the mouse while it runs, that closing flies back, and that a chest broken while open closes with glass.
 */
final class FlyScenario {
    /** Ticks after opening / closing that get a screenshot (the flight takes flyDurationTicks = 6 by default). */
    private static final int[] OPEN_SHOTS = {0, 1, 2, 3, 4, 8};
    private static final int[] CLOSE_SHOTS = {1, 2, 3, 4, 5, 6, 8};
    /** The test world loads in a few ticks; the use key needs the target block or entity to be in reach. */
    private static final int SETTLE_TICKS = 10;

    private FlyScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    Consumer<String> shot, String name) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        float openSpeed = config.get(Params.OPEN_SPEED);
        int flyTicks = config.get(Params.FLY_DURATION);
        config.effect = "fly";
        config.openEffect = ShatterConfig.OPEN_FLY_FROM_BLOCK;
        // Each screenshot takes about 2 ticks: an 8-tick flight still flies at the "t1" shot and has landed by "t8"
        // (the default 6 ticks / openSpeed 1.5 = 4 ticks is over before the second shot).
        config.set(Params.OPEN_SPEED, 1f);
        config.set(Params.FLY_DURATION, 8);
        try {
            BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
            // Chest two blocks ahead (south), slightly to the right, so the flight is visible across the screen.
            BlockPos chest = feet.offset(-1, 0, 3);
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest[facing=north]",
                    chest.getX(), chest.getY(), chest.getZ()));
            lookAt(context, singleplayer, feet, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);

            // No source (inventory key path): flies in from an edge, not from the chest it is looking at.
            int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(3);
            check.accept(context.computeOnClient(mc -> ShatterProfiler.openCount()) == opens + 1,
                    name + ": the inventory (no source) must fly in from an edge");
            context.waitTicks(10);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(60);
            opens = context.computeOnClient(mc -> ShatterProfiler.openCount());

            // Chest: fly in, then fly back.
            openWithUseKey(context, check, shot, name + "_chest", opens);
            flyBack(context, check, shot, name + "_chest", "fly");

            // Chest broken while open: closes with glass instead.
            int opensBefore = context.computeOnClient(mc -> ShatterProfiler.openCount());
            context.getInput().pressKey(options -> options.keyUse);
            context.waitFor(mc -> mc.screen instanceof AbstractContainerScreen<?>, 40);
            context.waitTicks(10);
            check.accept(context.computeOnClient(mc -> ShatterProfiler.openCount()) > opensBefore,
                    name + "_broken: the chest must fly in");
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air",
                    chest.getX(), chest.getY(), chest.getZ()));
            // The server closes the chest screen once the chest is gone; close it ourselves if it has not yet.
            context.waitTicks(3);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(2);
            String brokenEffect = context.computeOnClient(mc -> lastEffect());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}_broken: closed with {}", name, brokenEffect);
            check.accept("glass".equals(brokenEffect), name + "_broken: a broken chest must close with glass, was " + brokenEffect);
            context.waitTicks(80);

            // Chest minecart (an entity source).
            BlockPos cart = feet.offset(1, 0, 3);
            singleplayer.getServer().runCommand(String.format(Locale.ROOT,
                    "summon minecraft:chest_minecart %d.5 %d %d.5 {NoGravity:1b}", cart.getX(), cart.getY(), cart.getZ()));
            lookAt(context, singleplayer, feet, cart.getX() + 0.5, cart.getY() + 0.4, cart.getZ() + 0.5);
            int opensCart = context.computeOnClient(mc -> ShatterProfiler.openCount());
            openWithUseKey(context, check, shot, name + "_minecart", opensCart);
            flyBack(context, check, shot, name + "_minecart", "fly");
            singleplayer.getServer().runCommand("kill @e[type=minecraft:chest_minecart]");
            context.waitTicks(10);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            config.set(Params.OPEN_SPEED, openSpeed);
            config.set(Params.FLY_DURATION, flyTicks);
        }
    }

    static void lookAt(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos feet,
                               double x, double y, double z) {
        // Explicit yaw/pitch from the eyes: "tp ... facing" left the pitch level here, aiming over the chest.
        double eyeY = feet.getY() + context.computeOnClient(mc -> (double) mc.player.getEyeHeight());
        double dx = x - (feet.getX() + 0.5);
        double dy = y - eyeY;
        double dz = z - (feet.getZ() + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 %.2f %.2f",
                feet.getX(), feet.getY(), feet.getZ(), yaw, pitch));
        context.waitTicks(SETTLE_TICKS);
    }

    private static void openWithUseKey(ClientGameTestContext context, BiConsumer<Boolean, String> check,
                                       Consumer<String> shot, String name, int opensBefore) {
        String aim = context.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getType()));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: aiming at {}", name, aim);
        shot.accept(name + "_aim");
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(mc -> mc.screen instanceof AbstractContainerScreen<?>, 40);
        boolean started = context.computeOnClient(mc -> ShatterProfiler.openCount()) > opensBefore;
        boolean animating = false;
        int waited = 0;
        for (int tick : OPEN_SHOTS) {
            context.waitTicks(tick - waited);
            waited = tick;
            shot.accept(name + "_open_t" + tick);
            String progress = context.computeOnClient(mc -> FlyOpen.describeProgress());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: open t{} {}", name, tick, progress);
            if (tick == 1) {
                animating = context.computeOnClient(mc -> FlyOpen.isAnimating(mc.screen));
            }
        }
        boolean landed = !context.computeOnClient(mc -> FlyOpen.isAnimating(mc.screen));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: open started={} animating at t1={} landed by t{}={}",
                name, started, animating, waited, landed);
        check.accept(started, name + ": opening by using it must start the fly-in");
        check.accept(animating, name + ": the GUI must still be flying (mouse blocked) 1 tick after opening");
        check.accept(landed, name + ": the fly-in must be over after " + waited + " ticks");
    }

    private static void flyBack(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot,
                                String name, String expectedEffect) {
        context.runOnClient(mc -> mc.setScreen(null));
        int waited = 0;
        String effect = null;
        for (int tick : CLOSE_SHOTS) {
            context.waitTicks(tick - waited);
            waited = tick;
            if (effect == null) {
                effect = context.computeOnClient(mc -> lastEffect());
            }
            shot.accept(name + "_close_t" + tick);
        }
        context.waitTicks(5);
        int waves = context.computeOnClient(mc -> ShardManager.waveCount());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: closed with {}, waves left after the flight={}", name, effect, waves);
        check.accept(expectedEffect.equals(effect), name + ": must close with " + expectedEffect + ", was " + effect);
        check.accept(waves == 0, name + ": nothing may be left after the flight, waves=" + waves);
        context.waitTicks(20);
    }

    /** The effect of the newest wave, or "none". */
    private static String lastEffect() {
        return ShardManager.batches().isEmpty() ? "none" : ShardManager.batches().getLast().effect().id();
    }
}
