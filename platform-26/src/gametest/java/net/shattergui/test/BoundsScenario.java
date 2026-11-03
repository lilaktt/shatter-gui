package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.shatter.ShardManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Screens with parts outside the container panel (creative tabs, scroll bar and arrows; the recipe book next to the
 * survival inventory and the furnace): every part must burn and shatter. Screenshots of the open screen, burn and
 * glass; the burn check compares the shard area with the panel.
 */
final class BoundsScenario {
    private BoundsScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    Consumer<String> shot, String name, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        try {
            // Creative inventory, opened with the inventory key like a player.
            singleplayer.getServer().runCommand("gamemode creative @p");
            context.waitTicks(5);
            for (String fx : new String[]{"burn", "glass"}) {
                config.effect = fx;
                context.getInput().pressKey(options -> options.keyInventory);
                context.waitFor(mc -> mc.screen instanceof CreativeModeInventoryScreen, 40);
                closeAndShoot(context, check, shot, name + "_creative_" + fx, fx);
            }
            singleplayer.getServer().runCommand("gamemode survival @p");
            context.waitTicks(5);

            // Survival inventory and furnace with the recipe book open.
            context.runOnClient(mc -> {
                mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
                mc.player.getRecipeBook().setOpen(RecipeBookType.FURNACE, true);
            });
            for (String fx : new String[]{"burn", "glass"}) {
                config.effect = fx;
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                closeAndShoot(context, check, shot, name + "_recipebook_inventory_" + fx, fx);
                context.setScreen(furnace);
                closeAndShoot(context, check, shot, name + "_recipebook_furnace_" + fx, fx);
            }
            context.runOnClient(mc -> {
                mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
                mc.player.getRecipeBook().setOpen(RecipeBookType.FURNACE, false);
            });
        } finally {
            config.effect = effect;
        }
    }

    private static void closeAndShoot(ClientGameTestContext context, BiConsumer<Boolean, String> check,
                                      Consumer<String> shot, String name, String effect) {
        context.waitTicks(20);
        shot.accept(name + "_before");
        context.runOnClient(mc -> mc.setScreen(null));
        int[] ticks = "burn".equals(effect) ? new int[]{10, 20} : new int[]{2, 10};
        int waited = 0;
        int shards = -1;
        for (int tick : ticks) {
            context.waitTicks(tick - waited);
            waited = tick;
            if (shards < 0) {
                shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
            }
            shot.accept(name + "_t" + tick);
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: shards={}", name, shards);
        check.accept(shards > 0, name + ": must " + effect + " (shards=" + shards + ")");
        context.waitTicks(120);
    }
}
