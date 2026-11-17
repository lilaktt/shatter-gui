package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class ReloadLoopGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReloadLoop");
    private static final int TIMEOUT_TICKS = 1200;

    @Override
    public void runTest(ClientGameTestContext context) {
        int count = Integer.getInteger("shatter.reload.count", 20);
        boolean mod = FabricLoader.getInstance().isModLoaded("shatter_gui");
        LOGGER.info("[ReloadLoop] start: {} reloads, Shatter GUI loaded {}, mods {}", count, mod,
                FabricLoader.getInstance().getAllMods().size());
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            McTest.waitForChunksRender(singleplayer);
            context.waitTicks(40);
            for (int i = 1; i <= count; i++) {
                long start = System.nanoTime();
                CompletableFuture<Void> reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
                int waited = 0;
                while (waited < TIMEOUT_TICKS && !(reload.isDone() && context.computeOnClient(mc -> McTest.overlay(mc) == null))) {
                    context.waitTick();
                    waited++;
                }
                if (waited >= TIMEOUT_TICKS) {
                    throw new AssertionError("[ReloadLoop] reload " + i + " did not finish in " + TIMEOUT_TICKS + " ticks");
                }
                context.waitTicks(20);
                LOGGER.info("[ReloadLoop] reload {} of {} done in {} ms (Shatter GUI loaded {})", i, count,
                        (System.nanoTime() - start) / 1_000_000, mod);
            }
        }
        LOGGER.info("[ReloadLoop] all {} reloads done", count);
    }
}
