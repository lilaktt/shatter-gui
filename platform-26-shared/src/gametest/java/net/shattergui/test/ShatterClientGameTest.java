package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.FurnaceMenu;
import net.shattergui.ShatterGuiClient;
import net.shattergui.capture.CapturePool;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ShatterClientGameTest implements FabricClientGameTest {
    private static final int SHARD_LIFETIME_WAIT = 90;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("shatter.test.keepWindow")) {
            context.getInput().resizeWindow(1920, 1080);
        }
        context.waitTicks(5);

        if ("dist".equals(System.getProperty("shatter.test.only", "").trim())) {
            DistScenario.run(context, this::check, ShatterClientGameTest::furnace);
            if (!failures.isEmpty()) {
                throw new AssertionError("Shatter GUI test failures:\n - " + String.join("\n - ", failures));
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] all checks passed");
            return;
        }

        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        LinksScenario.everyPageUp();
        ShatterConfig config = ShatterConfig.get();
        config.profiling = true;
        config.applyTo = ShatterConfig.APPLY_ALL;
        config.effect = "glass";
        config.overrides.clear();
        config.disabledScreens.clear();

        int closes = closeCount(context);
        context.setScreen(TitleScreen::new);
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTicks(5);
        check(closeCount(context) == closes && liveShards(context) == 0, "title screen (no world) must not shatter");

        if (FabricLoader.getInstance().isModLoaded("iris")) {
            ShaderScenarios.selectPack(context);
        }

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            McTest.waitForChunksRender(singleplayer);
            context.waitTicks(20);
            check(closeCount(context) == closes, "level loading screen must not shatter");
            StartupScenario.run(context, this::check, ShatterClientGameTest::furnace);

            String irisOnly = System.getProperty("shatter.test.only", "");
            if (irisOnly.contains("matrix")) {
                MatrixScenario.startup(context, failures);
            }
            if (FabricLoader.getInstance().isModLoaded("iris") && (irisOnly.contains("gather") || irisOnly.contains("matrix")
                    || irisOnly.contains("duplicates") || irisOnly.contains("thematic") || irisOnly.contains("esc") || irisOnly.contains("ontop")
                    || irisOnly.contains("backdrop") || irisOnly.contains("follow") || irisOnly.contains("settle")
                    || irisOnly.contains("bob") || irisOnly.contains("hand") || irisOnly.contains("roof")
                    || irisOnly.contains("mask") || irisOnly.contains("water"))) {
                context.waitTicks(100);
                boolean withSpatial = FabricLoader.getInstance().isModLoaded("spatial-gui");
                String tag = "shader_" + ShaderScenarios.packTag() + (withSpatial ? "_spatial" : "");
                if (irisOnly.contains("matrix")) {
                    MatrixScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("gather")) {
                    GatherScenario.run(context, this::check, tag, !withSpatial, ShatterClientGameTest::furnace);
                }
                if (irisOnly.contains("duplicates")) {
                    DuplicateScenario.run(context, this::check, tag, ShatterClientGameTest::furnace);
                }
                if (irisOnly.contains("thematic")) {
                    ThematicPlaneScenario.run(context, this::check, tag, ShatterClientGameTest::furnace);
                }
                if (irisOnly.contains("esc")) {
                    EscScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("ontop")) {
                    OnTopScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("backdrop")) {
                    BackdropScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("follow")) {
                    FollowScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("settle")) {
                    SettleScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("bob")) {
                    BobScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("roof")) {
                    RoofScenario.run(context, singleplayer, this::check, "shaders");
                }
                if (irisOnly.contains("hand")) {
                    HandScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("mask")) {
                    ModelMaskScenario.run(context, singleplayer, this::check, tag);
                }
                if (irisOnly.contains("water")) {
                    WaterScenario.run(context, singleplayer, this::check, tag);
                }
            } else if (FabricLoader.getInstance().isModLoaded("iris")) {
                new ShaderScenarios(context, this::check).run(ShaderScenarios.packTag(), ShatterClientGameTest::furnace,
                        singleplayer);
            } else {
                runWithoutShaders(context, singleplayer, config);
            }
        }
        context.waitTicks(5);
        check(liveShards(context) == 0, "leaving the world must clear all shards");

        if (!failures.isEmpty()) {
            throw new AssertionError("Shatter GUI test failures:\n - " + String.join("\n - ", failures));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] all checks passed");
    }

    private void runWithoutShaders(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                                   ShatterConfig config) {
        boolean spatial = FabricLoader.getInstance().isModLoaded("spatial-gui");
        String only = System.getProperty("shatter.test.only", "").trim();
        if (!only.isEmpty()) {
            java.util.Set<String> chosen = java.util.Set.of(only.split(","));
            String prefix = spatial ? "spatial_" : "";
            ShatterGuiClient.LOGGER.info("[ShatterTest] only {} (Spatial GUI {})", chosen, spatial ? "loaded" : "not loaded");
            int repeat = Integer.getInteger("shatter.test.repeat", 1);
            for (int round = 1; round <= repeat; round++) {
                if (repeat > 1) {
                    ShatterGuiClient.LOGGER.info("[ShatterTest] round {} of {}, failures so far: {}", round, repeat, failures.size());
                    context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
                }
                if (chosen.contains("reload")) {
                    for (int i = 1; i <= Integer.getInteger("shatter.test.reloads", 20); i++) {
                        context.runOnClient(Minecraft::reloadResourcePacks);
                        context.waitFor(mc -> McTest.overlay(mc) == null, 1200);
                        context.waitTicks(5);
                        ShatterGuiClient.LOGGER.info("[ShatterTest] reload {} done", i);
                    }
                }
                if (chosen.contains("matrix")) {
                    if (spatial) {
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                    }
                    MatrixScenario.run(context, singleplayer, this::check, spatial ? "spatial_third" : "flat");
                }
                if (chosen.contains("shots")) {
                    EditorShotsScenario.run(context, this::check, context::takeScreenshot, prefix);
                }
                if (chosen.contains("crawler")) {
                    UiCrawlerScenario.run(context, this::check, context::takeScreenshot, prefix + "crawler");
                }
                if (chosen.contains("lang")) {
                    for (String code : System.getProperty("shatter.test.lang", "de_de,ja_jp,ru_ru").split(",")) {
                        LangScenario.run(context, this::check, context::takeScreenshot, code.trim());
                    }
                }
                if (chosen.contains("gather")) {
                    if (spatial) {
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", true);
                        setup.setSpatial("firstPersonModeContainers", true);
                        GatherScenario.run(context, this::check, "spatial_first", true, ShatterClientGameTest::furnace);
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                        GatherScenario.run(context, this::check, "spatial_third", false, ShatterClientGameTest::furnace);
                    } else {
                        GatherScenario.run(context, this::check, "flat", true, ShatterClientGameTest::furnace);
                    }
                }
                for (String which : List.of("duplicates", "thematic")) {
                    if (!chosen.contains(which)) {
                        continue;
                    }
                    java.util.function.Consumer<String> scenario = m -> {
                        if (which.equals("duplicates")) {
                            DuplicateScenario.run(context, this::check, m, ShatterClientGameTest::furnace);
                        } else {
                            ThematicPlaneScenario.run(context, this::check, m, ShatterClientGameTest::furnace);
                        }
                    };
                    if (spatial) {
                        String spatialMode = System.getProperty("shatter.test.spatialMode", "");
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        if (!spatialMode.equals("third")) {
                            setup.setSpatial("firstPersonModeInventory", true);
                            setup.setSpatial("firstPersonModeContainers", true);
                            scenario.accept("spatial_first");
                        }
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                        if (!spatialMode.equals("first")) {
                            scenario.accept("spatial_third");
                        }
                    } else {
                        scenario.accept("flat");
                    }
                }
                if (chosen.contains("esc")) {
                    if (spatial) {
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", true);
                        setup.setSpatial("firstPersonModeContainers", true);
                        EscScenario.run(context, singleplayer, this::check, "spatial_first");
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                        EscScenario.run(context, singleplayer, this::check, "spatial_third");
                    } else {
                        EscScenario.run(context, singleplayer, this::check, "flat");
                    }
                }
                if (chosen.contains("backdrop")) {
                    if (spatial) {
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", true);
                        setup.setSpatial("firstPersonModeContainers", true);
                        BackdropScenario.run(context, singleplayer, this::check, "spatial_first");
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                        BackdropScenario.run(context, singleplayer, this::check, "spatial_third");
                    } else {
                        BackdropScenario.run(context, singleplayer, this::check, "flat");
                    }
                }
                if (chosen.contains("diagnostics")) {
                    DiagnosticsScenario.run(context, this::check);
                }
                if (chosen.contains("follow")) {
                    if (spatial) {
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", true);
                        setup.setSpatial("firstPersonModeContainers", true);
                        FollowScenario.run(context, singleplayer, this::check, "spatial_first");
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                        FollowScenario.run(context, singleplayer, this::check, "spatial_third");
                    } else {
                        FollowScenario.run(context, singleplayer, this::check, "flat");
                    }
                }
                if (chosen.contains("leak")) {
                    LeakScenario.run(context, this::check, spatial ? "spatial" : "flat", ShatterClientGameTest::furnace);
                }
                if (chosen.contains("budget")) {
                    new EffectScenarios(context, this::check, context::takeScreenshot).configMigration("config_migration");
                    BudgetScenario.run(context, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("ontop")) {
                    if (spatial) {
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", true);
                        setup.setSpatial("firstPersonModeContainers", true);
                        OnTopScenario.run(context, singleplayer, this::check, "spatial_first");
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                        OnTopScenario.run(context, singleplayer, this::check, "spatial_third");
                    } else {
                        OnTopScenario.run(context, singleplayer, this::check, "flat");
                    }
                }
                if (chosen.contains("links")) {
                    LinksScenario.run(context, this::check, spatial ? "spatial_links" : "links");
                }
                if (chosen.contains("settle")) {
                    SettleScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("bob")) {
                    BobScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("roof")) {
                    RoofScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("hand")) {
                    HandScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("mask")) {
                    ModelMaskScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("water")) {
                    WaterScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
                }
                if (chosen.contains("spatialflow")) {
                    SpatialFlowScenario.run(context, singleplayer, this::check);
                }
                if (chosen.contains("vortex")) {
                    VortexScenario.run(context, singleplayer, this::check, context::takeScreenshot, prefix + "vortex",
                            ShatterClientGameTest::furnace);
                }
                if (chosen.contains("edge")) {
                    new EffectScenarios(context, this::check, context::takeScreenshot).edgeFlights(spatial ? "spatial_edge" : "edge");
                }
                if (chosen.contains("choose")) {
                    ChooseScenario.run(context, this::check, context::takeScreenshot, prefix + "choose");
                }
                if (chosen.contains("quickshots")) {
                    if (spatial) {
                        QuickShotsScenario.run(context, "spatial_third", ShatterClientGameTest::furnace);
                        SpatialScenarios setup = new SpatialScenarios(context, this::check);
                        setup.setSpatial("firstPersonModeInventory", true);
                        setup.setSpatial("firstPersonModeContainers", true);
                        QuickShotsScenario.run(context, "spatial_first", ShatterClientGameTest::furnace);
                        setup.setSpatial("firstPersonModeInventory", false);
                        setup.setSpatial("firstPersonModeContainers", false);
                    } else {
                        QuickShotsScenario.run(context, "flat", ShatterClientGameTest::furnace);
                    }
                }
                if (chosen.contains("bundled")) {
                    BundledEffectsScenario.run(context, this::check, context::takeScreenshot, prefix + "bundled",
                            ShatterClientGameTest::furnace);
                }
                if (chosen.contains("editor")) {
                    EditorScenario.run(context, this::check, context::takeScreenshot, prefix + "editor");
                }
                if (chosen.contains("tooltips")) {
                    TooltipScenario.run(context, this::check, context::takeScreenshot, prefix + "tooltips");
                }
                if (chosen.contains("realpreview")) {
                    RealPreviewScenario.run(context, singleplayer, this::check, context::takeScreenshot, prefix + "real_preview");
                }
                if (chosen.contains("collide")) {
                    CollideScenario.run(context, singleplayer, this::check, context::takeScreenshot, prefix + "collide",
                            ShatterClientGameTest::furnace);
                }
                if (chosen.contains("fxeditor")) {
                    FxEditorScenario.run(context, this::check, context::takeScreenshot, prefix + "fx_editor",
                            ShatterClientGameTest::furnace);
                }
            }
            return;
        }
        if (spatial) {
            ShatterGuiClient.LOGGER.info("[ShatterTest] Spatial GUI {} loaded, running its scenarios",
                    FabricLoader.getInstance().getModContainer("spatial-gui").orElseThrow().getMetadata().getVersion());
            new SpatialScenarios(context, this::check).run(ShatterClientGameTest::furnace);
        } else {
            ShatterGuiClient.LOGGER.info("[ShatterTest] Spatial GUI not loaded");
        }
        BoundsScenario.run(context, singleplayer, this::check, context::takeScreenshot,
                spatial ? "spatial_bounds" : "bounds", ShatterClientGameTest::furnace);
        FlyScenario.run(context, singleplayer, this::check, context::takeScreenshot, spatial ? "spatial_fly" : "fly");
        EscScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        OnTopScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        SettleScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        BobScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        HandScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        ModelMaskScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        WaterScenario.run(context, singleplayer, this::check, spatial ? "spatial" : "flat");
        SpatialFlowScenario.run(context, singleplayer, this::check);
        EffectScenarios effects = new EffectScenarios(context, this::check, context::takeScreenshot);
        String prefix = spatial ? "spatial_" : "";
        effects.edgeFlights(prefix + "edge");
        effects.dissolve(prefix + "dissolve", ShatterClientGameTest::furnace);
        effects.spam(prefix + "spam");
        BudgetScenario.run(context, this::check, spatial ? "spatial" : "flat");
        effects.edgeOnlyForFly(prefix + "edge_only_fly");
        effects.masterSwitch(prefix + "master_switch");
        effects.configMigration(prefix + "config_migration");
        EditorScenario.run(context, this::check, context::takeScreenshot, prefix + "editor");
        RealPreviewScenario.run(context, singleplayer, this::check, context::takeScreenshot, prefix + "real_preview");
        TooltipScenario.run(context, this::check, context::takeScreenshot, prefix + "tooltips");
        LinksScenario.run(context, this::check, prefix + "links");
        DiagnosticsScenario.run(context, this::check);
        VortexScenario.run(context, singleplayer, this::check, context::takeScreenshot, prefix + "vortex",
                ShatterClientGameTest::furnace);
        CustomScenario.run(context, this::check, context::takeScreenshot, prefix + "custom", ShatterClientGameTest::furnace);
        DataEffectsScenario.run(context, this::check, context::takeScreenshot, prefix + "fx", ShatterClientGameTest::furnace);
        CollideScenario.run(context, singleplayer, this::check, context::takeScreenshot, prefix + "collide",
                ShatterClientGameTest::furnace);
        BundledEffectsScenario.run(context, this::check, context::takeScreenshot, prefix + "bundled", ShatterClientGameTest::furnace);
        FxEditorScenario.run(context, this::check, context::takeScreenshot, prefix + "fx_editor", ShatterClientGameTest::furnace);

        closeAndCheck(context, "pause", () -> new PauseScreen(true), true);
        closeAndCheck(context, "chat", () -> new ChatScreen("", false), true);
        closeAndCheck(context, "inventory", () -> new InventoryScreen(Minecraft.getInstance().player), true);

        BurnScenario.run(context, this::check, context::takeScreenshot, "shatter_burn_furnace", ShatterClientGameTest::furnace);

        config.effect = "glass";
        config.applyTo = ShatterConfig.APPLY_CONTAINERS;
        closeAndCheck(context, "pause_containers_only", () -> new PauseScreen(true), false);
        closeAndCheck(context, "inventory_containers_only", () -> new InventoryScreen(Minecraft.getInstance().player), true);
        config.applyTo = ShatterConfig.APPLY_ALL;

        config.disabledScreens.add("minecraft:inventory");
        closeAndCheck(context, "inventory_disabled", () -> new InventoryScreen(Minecraft.getInstance().player), false);
        config.disabledScreens.clear();

        ChooseScenario.run(context, this::check, context::takeScreenshot, prefix + "choose");

        for (int i = 0; i < 30; i++) {
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(2);
            context.runOnClient(mc -> Mc26.setScreen(null));
            context.waitTicks(2);
        }
        int busyTextures = context.computeOnClient(mc -> CapturePool.texturesWithMemory());
        check(busyTextures <= CapturePool.MAX_SLOTS + 1, "pool must stay bounded, textures: " + busyTextures);
        context.waitTicks(SHARD_LIFETIME_WAIT);
        int idleTextures = context.computeOnClient(mc -> CapturePool.texturesWithMemory());
        ShatterGuiClient.LOGGER.info("[ShatterTest] textures in memory: {} during spam, {} after", busyTextures, idleTextures);
        check(idleTextures <= 2, "after shards die only backup + 1 idle texture may stay, textures: " + idleTextures);
        LeakScenario.run(context, this::check, spatial ? "spatial" : "flat", ShatterClientGameTest::furnace);
    }

    private void closeAndCheck(ClientGameTestContext context, String name, Supplier<Screen> screen, boolean shouldShatter) {
        context.setScreen(screen);
        context.waitTicks(5);
        int closes = closeCount(context);
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTicks(3);
        boolean shattered = closeCount(context) > closes;
        int live = liveShards(context);
        if (shouldShatter) {
            context.takeScreenshot("shatter_" + name + "_t3");
            context.waitTicks(15);
            context.takeScreenshot("shatter_" + name + "_t18");
            context.waitTicks(20);
            context.takeScreenshot("shatter_" + name + "_t38");
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: shattered={} shards={}", name, shattered, live);
        if (shouldShatter) {
            check(shattered && live > 0, name + " must shatter (shattered=" + shattered + ", shards=" + live + ")");
        } else {
            check(!shattered && live == 0, name + " must not shatter (shattered=" + shattered + ", shards=" + live + ")");
        }
        context.waitTicks(SHARD_LIFETIME_WAIT);
        check(liveShards(context) == 0, name + ": shards and their texture must be gone after their lifetime");
    }

    private static Screen furnace() {
        Minecraft mc = Minecraft.getInstance();
        FurnaceMenu menu = new FurnaceMenu(0, mc.player.getInventory());
        return new FurnaceScreen(menu, mc.player.getInventory(), Component.literal("Furnace"));
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
            ShatterGuiClient.LOGGER.error("[ShatterTest] FAILED: {}", message);
        }
    }

    private static int closeCount(ClientGameTestContext context) {
        return context.computeOnClient(mc -> ShatterProfiler.closeCount());
    }

    private static int liveShards(ClientGameTestContext context) {
        return context.computeOnClient(mc -> ShardManager.liveShardCount());
    }
}
