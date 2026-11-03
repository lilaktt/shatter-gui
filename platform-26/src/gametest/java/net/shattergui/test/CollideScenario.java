package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The collide module in the world: an own schema-2 effect with gravity over a stone floor. Every tick every shard's
 * centre must stay above the floor's top face; the pieces must end up lying on it. The same with bounce, where pieces
 * must also fly back up after hitting the floor (and still never go below it).
 */
final class CollideScenario {
    private static final String LAND = "test_collide_fx";
    private static final String BOUNCE = "test_collide_bounce_fx";

    private CollideScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    Consumer<String> shot, String name, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        Path land = FxEffects.EFFECTS.resolve(LAND + ".json");
        Path bounce = FxEffects.EFFECTS.resolve(BOUNCE + ".json");
        try {
            BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
            // A flat stone floor under and in front of the player, air above it.
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
                    feet.getX() - 5, feet.getY() - 1, feet.getZ() - 2, feet.getX() + 5, feet.getY() - 1, feet.getZ() + 6));
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
                    feet.getX() - 5, feet.getY(), feet.getZ() - 2, feet.getX() + 5, feet.getY() + 4, feet.getZ() + 6));
            // Looking down, so the floor the pieces land on is in the screenshots (the plane tilts with the camera).
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 40",
                    feet.getX(), feet.getY(), feet.getZ()));
            context.waitTicks(10);
            double floorTop = feet.getY();

            Files.createDirectories(FxEffects.EFFECTS);
            Files.writeString(land, effect(LAND, 0, "lie"), StandardCharsets.UTF_8);
            Files.writeString(bounce, effect(BOUNCE, 0.7f, "lie"), StandardCharsets.UTF_8);
            context.runOnClient(mc -> FxEffects.reloadAll());
            check.accept(context.computeOnClient(mc -> FxEffects.get(LAND) != null && FxEffects.get(BOUNCE) != null),
                    name + ": both test effects must load " + context.computeOnClient(mc -> FxEffects.messages()));
            config.openEffect = ShatterConfig.OPEN_NONE;

            Result still = play(context, shot, name + "_land", LAND, furnace, config, floorTop);
            check.accept(still.shards > 0, name + "_land: must spawn shards");
            check.accept(still.lowest >= floorTop - 0.01, name + "_land: a shard went below the floor: lowest centre "
                    + still.lowest + ", floor top " + floorTop);
            check.accept(still.landedShare >= 0.8, name + "_land: shards must lie on the floor, only "
                    + Math.round(still.landedShare * 100) + "% within 0.4 of it");
            check.accept(still.maxRise < 0.02, name + "_land: without bounce nothing flies back up (" + still.maxRise + ")");

            Result bouncy = play(context, shot, name + "_bounce", BOUNCE, furnace, config, floorTop);
            check.accept(bouncy.lowest >= floorTop - 0.01, name + "_bounce: a shard went below the floor: lowest centre "
                    + bouncy.lowest + ", floor top " + floorTop);
            check.accept(bouncy.maxRise > 0.05, name + "_bounce: shards must bounce back up (" + bouncy.maxRise + ")");
            check.accept(bouncy.landedShare >= 0.8, name + "_bounce: shards must settle on the floor, only "
                    + Math.round(bouncy.landedShare * 100) + "%");
        } catch (IOException e) {
            check.accept(false, name + ": " + e);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            try {
                Files.deleteIfExists(land);
                Files.deleteIfExists(bounce);
            } catch (IOException ignored) {
                // Left over in the test run folder only.
            }
            context.runOnClient(mc -> FxEffects.reloadAll());
        }
    }

    /** Pieces only fall (no push, no spin), so any upward motion is a bounce. */
    private static String effect(String id, float bounce, String rest) {
        return String.format(Locale.ROOT, """
                {"schema": 2, "id": "%s", "duration": 100,
                 "fracture": {"mode": "grid", "count": 30},
                 "trigger": {"type": "none", "spread": 0},
                 "appearance": {"alpha": 1},
                 "layers": [
                   {"module": "gravity", "params": {"strength": 0.03}},
                   {"module": "collide", "params": {"bounce": %s, "friction": 0.5, "rest": "%s"}}
                 ]}
                """, id, bounce, rest);
    }

    private record Result(int shards, double lowest, double maxRise, double landedShare) {
    }

    private static Result play(ClientGameTestContext context, Consumer<String> shot, String name, String id,
                               Supplier<Screen> furnace, ShatterConfig config, double floorTop) {
        config.effect = id;
        context.setScreen(furnace);
        context.waitTicks(15);
        context.runOnClient(mc -> mc.setScreen(null));
        context.waitTicks(1);
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        double lowest = Double.MAX_VALUE;
        double maxRise = 0;
        double landedShare = 0;
        for (int t = 1; t <= 80; t++) {
            double[] s = context.computeOnClient(mc -> {
                double low = Double.MAX_VALUE;
                double rise = 0;
                int near = 0;
                int count = 0;
                for (ShardManager.Batch batch : ShardManager.batches()) {
                    for (ShardInstance shard : batch.shards()) {
                        low = Math.min(low, shard.pos().y);
                        rise = Math.max(rise, shard.pos().y - shard.prevPos().y);
                        near += shard.pos().y < floorTop + 0.4 ? 1 : 0;
                        count++;
                    }
                }
                return new double[]{low, rise, count == 0 ? 0 : near / (double) count};
            });
            lowest = Math.min(lowest, s[0]);
            // The first ticks are the spawn (prevPos = spawn point); only motion after that counts.
            if (t > 2) {
                maxRise = Math.max(maxRise, s[1]);
            }
            landedShare = s[2];
            if (t == 5 || t == 15 || t == 40) {
                shot.accept(name + "_t" + t);
            }
            context.waitTick();
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} shards, lowest centre {} (floor top {}), max rise {}, on the floor {}%",
                name, shards, lowest, floorTop, maxRise, Math.round(landedShare * 100));
        context.waitFor(mc -> ShardManager.waveCount() == 0, 400);
        return new Result(shards, lowest, maxRise, landedShare);
    }
}
