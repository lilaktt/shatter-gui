package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.config.ShatterConfig;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.BundledParams;
import net.shattergui.shatter.ShardManager;

import java.util.List;
import java.util.function.Supplier;

/**
 * Screenshots of card flip, recede and door at 10, 30, 50, 70 and 90 % of their time, closing and opening a furnace
 * ({@code -PtestOnly=quickshots}, with {@code -PspatialGui=1.8} on Spatial GUI's plane): {@code quick_<mode>_<id>_<close|open>_<pct>.png}.
 * Frames are read back from the frame itself, so the moments are exact (no extra frame is drawn for the picture).
 */
final class QuickShotsScenario {
    private static final List<String> IDS = List.of("flip", "recede", "door");
    private static final int[] PERCENT = {10, 30, 50, 70, 90};

    private QuickShotsScenario() {
    }

    static void run(ClientGameTestContext context, String mode, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        try {
            for (String id : IDS) {
                int duration = BundledParams.fileDuration(id);
                // Closing.
                config.openEffect = ShatterConfig.OPEN_NONE;
                config.effect = id;
                context.runOnClient(mc -> mc.setScreen(furnace.get()));
                context.waitTicks(30);
                context.runOnClient(mc -> mc.setScreen(null));
                int waited = 0;
                while (waited < 10 && context.computeOnClient(mc -> ShardManager.waveCount() == 0)) {
                    context.waitTick();
                    waited++;
                }
                shots(context, "quick_" + mode + "_" + id + "_close", duration);
                clear(context);
                // Opening (closing again with the quick pop).
                config.effect = "pop";
                config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + id;
                context.runOnClient(mc -> mc.setScreen(furnace.get()));
                waited = 0;
                while (waited < 20 && context.computeOnClient(mc -> OpenAssemble.waveTicks() == 0)) {
                    context.waitTick();
                    waited++;
                }
                int length = context.computeOnClient(mc -> OpenAssemble.waveTicks());
                shots(context, "quick_" + mode + "_" + id + "_open", Math.max(1, length));
                context.waitTicks(10);
                context.runOnClient(mc -> mc.setScreen(null));
                clear(context);
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
        }
    }

    private static void shots(ClientGameTestContext context, String name, int duration) {
        int at = 0;
        for (int pct : PERCENT) {
            int tick = Math.max(at + (at == 0 ? 0 : 1), Math.round(duration * pct / 100f));
            context.waitTicks(tick - at);
            at = tick;
            FrameGrab.request(context, name + "_" + pct, true);
        }
        for (int pct : PERCENT) {
            FrameGrab.get(context, name + "_" + pct);
        }
    }

    private static void clear(ClientGameTestContext context) {
        context.waitTicks(4);
        for (int i = 0; i < 300 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
        context.waitTicks(3);
    }
}
