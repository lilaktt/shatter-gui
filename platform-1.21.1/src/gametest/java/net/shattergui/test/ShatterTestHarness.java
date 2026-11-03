package net.shattergui.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.shattergui.ShatterMod;
import net.shattergui.config.ShatterConfig;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The 1.21.1 test harness (Fabric API for 1.21.1 has no client game tests): with {@code -Dshatter.test=true}
 * ({@code runClientTest}) it waits for the title screen, creates a fresh flat creative world, runs the scenarios on a
 * thread of its own (each step on the client thread), logs every check as {@code [ShatterTest] OK/FAIL}, writes
 * screenshots to {@code build/run/clientTest/screenshots} and quits the game. {@code -PtestOnly=a,b} picks scenarios.
 */
public final class ShatterTestHarness implements ClientModInitializer {
    private static final Map<String, Consumer<TestContext>> SCENARIOS = new LinkedHashMap<>();

    static {
        SCENARIOS.put("glass", CloseScenarios::glass);
        SCENARIOS.put("collide", CloseScenarios::collide);
        SCENARIOS.put("ontop", CloseScenarios::onTop);
    }

    private static boolean started;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("shatter.test")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> TestContext.onTick());
        HudRenderCallback.EVENT.register((graphics, delta) -> TestContext.onFrame());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (started) {
                return;
            }
            if (screen instanceof AccessibilityOnboardingScreen) {
                // The first start of a fresh game folder asks about accessibility first.
                client.options.onboardAccessibility = false;
                client.execute(() -> client.setScreen(new TitleScreen(false)));
            } else if (screen instanceof TitleScreen) {
                started = true;
                quietOptions(client);
                Thread thread = new Thread(ShatterTestHarness::run, "Shatter GUI test");
                thread.setDaemon(true);
                thread.start();
                Thread watchdog = new Thread(() -> {
                    try {
                        thread.join(Long.getLong("shatter.test.timeoutMinutes", 20) * 60_000);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                    if (thread.isAlive()) {
                        ShatterMod.LOGGER.error("[ShatterTest] RESULT: timed out, stopping the game");
                        client.execute(client::stop);
                    }
                }, "Shatter GUI test watchdog");
                watchdog.setDaemon(true);
                watchdog.start();
            }
        });
    }

    /** No pause on focus loss, no tutorial, no narrator, the GUI scale tests expect. */
    private static void quietOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.narrator().set(NarratorStatus.OFF);
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
    }

    private static void run() {
        TestContext context = new TestContext();
        String only = System.getProperty("shatter.test.only", "");
        List<String> chosen = only.isBlank() ? List.copyOf(SCENARIOS.keySet())
                : Arrays.stream(only.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        try {
            context.waitTicks(20);
            createWorld(context);
            for (String name : chosen) {
                Consumer<TestContext> scenario = SCENARIOS.get(name);
                if (scenario == null) {
                    context.check(false, "unknown scenario " + name + " (known: " + SCENARIOS.keySet() + ")");
                    continue;
                }
                context.log("--- scenario {} ---", name);
                try {
                    scenario.accept(context);
                } catch (Throwable t) {
                    ShatterMod.LOGGER.error("[ShatterTest] scenario {} threw", name, t);
                    context.check(false, name + " threw " + t);
                }
            }
        } catch (Throwable t) {
            ShatterMod.LOGGER.error("[ShatterTest] harness failed", t);
            context.check(false, "harness: " + t);
        }
        if (context.failures().isEmpty()) {
            context.log("RESULT: all {} checks passed", context.checks());
        } else {
            context.log("RESULT: {} of {} checks failed: {}", context.failures().size(), context.checks(),
                    context.failures());
        }
        context.runOnClient(Minecraft::stop);
    }

    /** A flat creative world with commands, always noon and clear, nothing spawning. */
    private static void createWorld(TestContext context) {
        context.runOnClient(mc -> {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings("shatter_test", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                    true, rules, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("shatter_test", settings, new WorldOptions(1L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
        });
        boolean loaded = context.waitFor(mc -> mc.level != null && mc.player != null && mc.screen == null, 1200);
        context.check(loaded, "the test world loads");
        context.waitFor(mc -> mc.levelRenderer.hasRenderedAllSections(), 400);
        context.command("time set noon");
        context.runOnClient(mc -> {
            ShatterConfig config = ShatterConfig.get();
            config.profiling = true;
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
        });
        context.waitTicks(20);
    }
}
