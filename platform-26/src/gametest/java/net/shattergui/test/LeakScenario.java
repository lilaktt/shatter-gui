package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.capture.CapturePool;
import net.shattergui.config.ShatterConfig;
import net.shattergui.render.OnTopPass;
import net.shattergui.shatter.ShardManager;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * No texture leaks ({@code -PtestOnly=leak}): 100 quick opens and closes in a row (inventory and furnace, an opening
 * that gathers and close effects that take a capture each), with both policies of effects at once. While it runs the
 * texture pool stays within {@link CapturePool#MAX_SLOTS} + backup; once every effect is over at most the backup and
 * one idle slot keep memory, no wave is left and nothing waits to be drawn over the world.
 */
final class LeakScenario {
    private static final int CYCLES = 100;

    private LeakScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String mode, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        String policy = config.concurrentPolicy;
        Integer max = config.maxConcurrentEffects;
        try {
            config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + "pop";
            config.maxConcurrentEffects = 8;
            String[] effects = {"glass", "burn", "frost", "dissolve"};
            String[] policies = {ShatterConfig.POLICY_FINISH_OLDEST, ShatterConfig.POLICY_SKIP_NEW};
            int busiest = 0;
            for (int i = 0; i < CYCLES; i++) {
                config.effect = effects[i % effects.length];
                config.concurrentPolicy = policies[(i / 50) % 2];
                Supplier<Screen> screen = i % 2 == 0 ? () -> new InventoryScreen(Minecraft.getInstance().player) : furnace;
                context.runOnClient(mc -> mc.setScreen(screen.get()));
                context.waitTick();
                context.runOnClient(mc -> mc.setScreen(null));
                context.waitTick();
                busiest = Math.max(busiest, context.computeOnClient(mc -> CapturePool.texturesWithMemory()));
            }
            for (int i = 0; i < 1200 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
                context.waitTick();
            }
            context.waitTicks(5);
            int idle = context.computeOnClient(mc -> CapturePool.texturesWithMemory());
            int waves = context.computeOnClient(mc -> ShardManager.waveCount());
            boolean pending = context.computeOnClient(mc -> OnTopPass.hasPending());
            ShatterGuiClient.LOGGER.info("[ShatterTest] leak_{}: {} opens and closes, textures in memory at most {} while "
                    + "running, {} after, waves left {}, over-the-world geometry pending {}", mode, CYCLES, busiest, idle,
                    waves, pending);
            check.accept(busiest <= CapturePool.MAX_SLOTS + 1, "leak_" + mode + ": the texture pool must stay bounded, "
                    + busiest + " textures");
            check.accept(idle <= 2, "leak_" + mode + ": after the effects only the backup and one idle texture may stay, "
                    + idle + " textures");
            check.accept(waves == 0, "leak_" + mode + ": no wave may be left, " + waves);
            check.accept(!pending, "leak_" + mode + ": nothing may wait to be drawn over the world without effects");
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.concurrentPolicy = policy;
            config.maxConcurrentEffects = max;
        }
    }
}
