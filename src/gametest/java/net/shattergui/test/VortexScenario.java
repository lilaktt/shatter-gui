package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.custom.CustomEffectDef;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The vortex: screenshots across the effect for each target point (GUI centre, player, a real chest opened with the use
 * key), checks that the shards are pulled together (and into the chest for "source"), that everything is gone after
 * the effect, and that reduceMotion makes it shorter.
 */
final class VortexScenario {
    private static final int[] SHOTS = {1, 3, 6, 10, 14, 18, 20, 22, 24, 25, 26, 28};

    private VortexScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    Consumer<String> shot, String name, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        config.effect = "vortex";
        config.openEffect = ShatterConfig.OPEN_NONE;
        BlockPos chest = null;
        try {
            retiredKeys(check, name);
            for (String mode : new String[]{"center", "player"}) {
                config.set(Params.VORTEX_TARGET, mode);
                context.setScreen(furnace);
                context.waitTicks(20);
                shot.accept(name + "_" + mode + "_before");
                closeAndFollow(context, check, shot, name + "_" + mode, config, null);
            }

            // Source: a chest opened with the use key; the shards end up in it.
            config.set(Params.VORTEX_TARGET, "source");
            BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
            chest = feet.offset(1, 0, 3);
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest[facing=north]",
                    chest.getX(), chest.getY(), chest.getZ()));
            FlyScenario.lookAt(context, singleplayer, feet, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitFor(mc -> mc.screen instanceof AbstractContainerScreen<?>, 40);
            context.waitTicks(15);
            shot.accept(name + "_source_before");
            closeAndFollow(context, check, shot, name + "_source", config,
                    new Vector3d(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5));

            // reduceMotion: shorter.
            config.set(Params.VORTEX_TARGET, "center");
            config.reduceMotion = true;
            context.setScreen(furnace);
            context.waitTicks(10);
            context.runOnClient(mc -> mc.setScreen(null));
            int calm = Math.round(config.get(Params.VORTEX_DURATION) * 0.65f) + 14;
            context.waitTicks(6);
            shot.accept(name + "_reduce_motion_t6");
            context.waitTicks(calm - 6);
            int left = context.computeOnClient(mc -> ShardManager.waveCount());
            check.accept(left == 0, name + "_reduce_motion: must be over after " + calm + " ticks, waves=" + left);
        } finally {
            config.reduceMotion = false;
            config.set(Params.VORTEX_TARGET, "center");
            config.effect = effect;
            config.openEffect = openEffect;
            if (chest != null) {
                singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air",
                        chest.getX(), chest.getY(), chest.getZ()));
            }
            context.waitTicks(10);
        }
    }

    /** The removed flashIntensity/ringParticles in an old config and an effect file: read fine, dropped silently. */
    private static void retiredKeys(BiConsumer<Boolean, String> check, String name) {
        ShatterConfig old = ShatterConfig.parse("{\"effect\":\"vortex\",\"effects\":{\"vortex\":{\"params\":"
                + "{\"flashIntensity\":0.4,\"ringParticles\":16,\"spiralTurns\":3}}}}");
        String saved = old.toJsonString();
        check.accept(old.get(Params.VORTEX_TURNS) == 3f && !saved.contains("flashIntensity") && !saved.contains("ringParticles"),
                name + ": an old config with flashIntensity/ringParticles must load and drop them: " + saved.replace('\n', ' '));
        try {
            CustomEffectDef def = CustomEffectDef.parse("{\"schema\":1,\"id\":\"old_vortex\",\"base\":\"vortex\",\"params\":"
                    + "{\"flashIntensity\":0.4,\"ringParticles\":16,\"spinSpeed\":0.5}}", "test");
            check.accept(def.params().size() == 1 && def.get(Params.VORTEX_SPIN) == 0.5f,
                    name + ": an effect file with flashIntensity/ringParticles must load and drop them: " + def.params());
        } catch (CustomEffectDef.InvalidEffectException e) {
            check.accept(false, name + ": an effect file with flashIntensity/ringParticles must load: " + e.getMessage());
        }
    }

    private static void closeAndFollow(ClientGameTestContext context, BiConsumer<Boolean, String> check,
                                       Consumer<String> shot, String name, ShatterConfig config, Vector3d into) {
        // Ticks counted on the client clock (the level clock runs on the server thread): a screenshot may itself take ticks (the shader runs wait 2 per shot).
        long start = context.computeOnClient(mc -> (long) mc.gui.getGuiTicks());
        context.runOnClient(mc -> mc.setScreen(null));
        String kind = null;
        double startSpread = -1;
        double lateSpread = -1;
        Vector3d lateCentre = null;
        int lateAge = -1;
        for (int tick : SHOTS) {
            long elapsed = context.computeOnClient(mc -> (long) mc.gui.getGuiTicks()) - start;
            if (elapsed > tick) {
                continue;
            }
            context.waitTicks((int) (tick - elapsed));
            if (kind == null) {
                kind = context.computeOnClient(mc -> lastEffect());
            }
            double[] spread = context.computeOnClient(mc -> spread());
            if (tick == 3) {
                startSpread = spread[0];
            } else if (tick == 22 && spread[0] >= 0) {
                // Most shards are near the end of their flight (the whole vortex takes about vortexDuration = 24).
                lateSpread = spread[0];
                lateCentre = new Vector3d(spread[1], spread[2], spread[3]);
                lateAge = (int) spread[4];
            }
            shot.accept(name + "_t" + tick);
        }
        int total = config.get(Params.VORTEX_DURATION) + 16;
        long elapsed = context.computeOnClient(mc -> (long) mc.gui.getGuiTicks()) - start;
        context.waitTicks((int) Math.max(0, total - elapsed));
        int left = context.computeOnClient(mc -> ShardManager.waveCount());
        double toTarget = into != null && lateCentre != null ? lateCentre.distance(into) : -1;
        ShatterGuiClient.LOGGER.info(String.format(Locale.ROOT,
                "[ShatterTest] %s: effect=%s spread t3=%.3f t22=%.3f (oldest shard %d ticks) centre-to-chest t22=%.2f waves after %d ticks=%d",
                name, kind, startSpread, lateSpread, lateAge, toTarget, total, left));
        check.accept("vortex".equals(kind), name + ": must close with vortex, was " + kind);
        check.accept(startSpread > 0 && lateSpread >= 0 && lateSpread < startSpread * 0.6,
                name + ": shards must be pulled together (spread " + startSpread + " -> " + lateSpread + ")");
        if (into != null) {
            check.accept(toTarget >= 0 && toTarget < 0.8, name + ": shards must end in the chest, distance " + toTarget);
        }
        check.accept(left == 0, name + ": must be gone after " + total + " ticks, waves=" + left);
        context.waitTicks(10);
    }

    /** {mean distance of live vortex shards from their centre, centre x, y, z}, or -1 without shards. */
    private static double[] spread() {
        if (ShardManager.batches().isEmpty()) {
            return new double[]{-1, 0, 0, 0, -1};
        }
        List<ShardInstance> shards = ShardManager.batches().getLast().shards();
        if (shards.isEmpty()) {
            return new double[]{-1, 0, 0, 0, -1};
        }
        Vector3d centre = new Vector3d();
        for (ShardInstance shard : shards) {
            centre.add(shard.pos());
        }
        centre.div(shards.size());
        double sum = 0;
        int age = 0;
        for (ShardInstance shard : shards) {
            sum += shard.pos().distance(centre);
            age = Math.max(age, shard.age());
        }
        return new double[]{sum / shards.size(), centre.x, centre.y, centre.z, age};
    }

    private static String lastEffect() {
        return ShardManager.batches().isEmpty() ? "none" : ShardManager.batches().getLast().effect().id();
    }
}