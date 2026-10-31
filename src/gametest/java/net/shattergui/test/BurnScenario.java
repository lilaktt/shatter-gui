package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The burn effect over its whole life: screenshots at the start of the fire front, in the middle, when it is almost
 * burnt, during the smoke and after, plus checks that only smoke is left after the burn and nothing after the smoke.
 */
final class BurnScenario {
    private BurnScenario() {
    }

    /**
     * @param shot takes a screenshot with the given name (differs with and without Sodium)
     */
    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot,
                    String name, Supplier<Screen> screen) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        config.effect = "burn";
        try {
            int burnTicks = config.get(Params.BURN_DURATION);
            int smokeTicks = config.get(Params.BURN_SMOKE_DURATION);
            context.setScreen(screen);
            context.waitTicks(30);
            shot.accept(name + "_before");
            context.runOnClient(mc -> mc.setScreen(null));

            // Fire front: just started, a third, two thirds, nearly done (cells take ~17 ticks to burn out).
            int[] burnShots = {3, burnTicks / 3, burnTicks * 2 / 3, burnTicks + 8};
            int waited = 0;
            int shardsMidway = -1;
            for (int tick : burnShots) {
                context.waitTicks(tick - waited);
                waited = tick;
                shot.accept(name + "_t" + tick);
                if (tick == burnTicks / 3) {
                    shardsMidway = context.computeOnClient(mc -> ShardManager.liveShardCount());
                }
            }

            // After every cell has burnt (front + 17 ticks per cell): no shards, only the smoke keeps the wave alive.
            int smokeTick = burnTicks + 30;
            context.waitTicks(smokeTick - waited);
            waited = smokeTick;
            shot.accept(name + "_smoke_t" + smokeTick);
            int shardsAfterBurn = context.computeOnClient(mc -> ShardManager.liveShardCount());
            int wavesDuringSmoke = context.computeOnClient(mc -> ShardManager.waveCount());

            int lateSmokeTick = burnTicks + 17 + smokeTicks * 3 / 4;
            context.waitTicks(lateSmokeTick - waited);
            waited = lateSmokeTick;
            shot.accept(name + "_smoke_t" + lateSmokeTick);

            // The wave stays alive while any of its particles does, so no wave here also means no smoke left.
            int endTick = burnTicks + 17 + smokeTicks + 5;
            context.waitTicks(endTick - waited);
            int wavesAfterSmoke = context.computeOnClient(mc -> ShardManager.waveCount());
            shot.accept(name + "_end_t" + endTick);

            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: shards midway={} after burn={}, waves during smoke={} after={}",
                    name, shardsMidway, shardsAfterBurn, wavesDuringSmoke, wavesAfterSmoke);
            check.accept(shardsMidway > 0, name + ": cells must still be burning midway");
            check.accept(shardsAfterBurn == 0, name + ": nothing of the screen may be left after the burn, shards=" + shardsAfterBurn);
            check.accept(wavesDuringSmoke == 1, name + ": the smoke must keep the wave alive, waves=" + wavesDuringSmoke);
            check.accept(wavesAfterSmoke == 0, name + ": the wave must end after the smoke, waves=" + wavesAfterSmoke);
            context.waitTicks(20);
        } finally {
            config.effect = effect;
        }
    }
}
