package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.FrameAudit;
import net.shattergui.shatter.ShardManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Thematic mode ({@code -PtestOnly=thematic}; with {@code -PspatialGui=1.8} in the first and the third person): furnace,
 * chest, anvil, crafting table, enchanting table and inventory close with their thematic effect, once as shipped
 * (burn, fly, explosion, glass, runes, the general effect) and once with fly and pop for some of them. From the second
 * frame after the close on, Spatial GUI's plane is never drawn — only our effect — and no frame shows the interface
 * twice; the effect runs (draws) and ends.
 */
final class ThematicPlaneScenario {
    private ThematicPlaneScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String mode, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        boolean thematic = config.thematic;
        String effect = config.effect;
        String open = config.openEffect;
        Map<String, String> themes = new LinkedHashMap<>(config.themes);
        Map<String, Supplier<Screen>> screens = new LinkedHashMap<>();
        screens.put("furnace", furnace);
        screens.put("chest", () -> {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            return new ContainerScreen(ChestMenu.threeRows(0, inv), inv, Component.literal("Chest"));
        });
        screens.put("anvil", () -> {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            return new AnvilScreen(new AnvilMenu(0, inv), inv, Component.literal("Anvil"));
        });
        screens.put("crafting", () -> {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            return new CraftingScreen(new CraftingMenu(0, inv), inv, Component.literal("Crafting"));
        });
        screens.put("enchanting", () -> {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            return new EnchantmentScreen(new EnchantmentMenu(0, inv), inv, Component.literal("Enchant"));
        });
        screens.put("inventory", () -> new InventoryScreen(Minecraft.getInstance().player));
        context.runOnClient(mc -> FrameAudit.enable(true));
        try {
            config.thematic = true;
            config.effect = "glass";
            config.openEffect = ShatterConfig.OPEN_NONE;
            for (Map<String, String> table : List.of(Map.<String, String>of(),
                    Map.of("furnaces", "fly", "crafting", "pop", "anvil", "pop", "enchanting", "fly"))) {
                config.themes.clear();
                config.themes.putAll(table);
                for (Map.Entry<String, Supplier<Screen>> s : screens.entrySet()) {
                    one(context, check, mode + (table.isEmpty() ? "" : "_fly_pop") + "_" + s.getKey(), s.getValue());
                }
            }
        } finally {
            config.thematic = thematic;
            config.effect = effect;
            config.openEffect = open;
            config.themes.clear();
            config.themes.putAll(themes);
            context.runOnClient(mc -> FrameAudit.enable(false));
        }
    }

    private static void one(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name,
                            Supplier<Screen> screen) {
        waitClear(context);
        context.runOnClient(mc -> mc.setScreen(screen.get()));
        context.waitTicks(10);
        long shown = context.computeOnClient(mc -> FrameAudit.next());
        boolean onPlane = context.computeOnClient(mc -> net.shattergui.compat.spatial.SpatialGuiCompat.isShowing(mc.screen));
        context.waitTicks(20);
        // The count itself works: while the screen is open it is on screen exactly once, as the plane on Spatial GUI.
        for (FrameAudit.Frame f : context.computeOnClient(mc -> FrameAudit.since(shown))) {
            if (f.instances() != 1 || f.planeVisible() != onPlane) {
                check.accept(false, "thematic " + name + ": open screen counted wrong: " + f + " (on plane " + onPlane + ")");
                break;
            }
        }
        long closed = context.computeOnClient(mc -> FrameAudit.next());
        context.runOnClient(mc -> mc.setScreen(null));
        context.waitTicks(2);
        String ran = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                : ShardManager.batches().getLast().effect().id());
        waitClear(context);
        context.waitTicks(3);
        List<FrameAudit.Frame> frames = context.computeOnClient(mc -> FrameAudit.since(closed));
        int drawn = 0;
        String bad = null;
        for (int i = 0; i < frames.size(); i++) {
            FrameAudit.Frame f = frames.get(i);
            drawn += f.closes();
            // Frame 0 and 1: Spatial GUI may still draw its last frame before the shards replace it.
            if (bad == null && (i >= 2 && f.planeVisible() || f.instances() > 1)) {
                bad = "frame " + i + ": " + f;
            }
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] thematic {}: ran {}, {} frames, {} with our effect, problem: {}", name,
                ran, frames.size(), drawn, bad);
        check.accept(bad == null, "thematic " + name + ": only our effect may show the closed interface, " + bad);
        check.accept(drawn > 0 && !"none".equals(ran), "thematic " + name + ": the effect " + ran + " must run and draw");
        check.accept(frames.getLast().instances() == 0, "thematic " + name + ": nothing left after the effect");
    }

    private static void waitClear(ClientGameTestContext context) {
        context.waitTicks(3);
        for (int i = 0; i < 600 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
    }
}
