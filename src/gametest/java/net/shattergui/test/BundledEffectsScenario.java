package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.editor.FxEditor;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.fx.FxSimulation;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.params.BundledParams;
import net.shattergui.shatter.ShardManager;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The effects shipped with the mod: every one loads without a message, closes a furnace (screenshots at three
 * moments) and ends cleanly; some open it by gathering (the screen stays hidden until the pieces have landed); the
 * glitch with reduce motion; the Effects tab lists them with their settings and "Copy to my effects" opens a copy in
 * the editor.
 */
final class BundledEffectsScenario {
    private static final List<String> OPEN = List.of("frost", "portal", "blinds", "explosion", "pixelate", "pop", "slide",
            "flip", "door", "iris", "spring");

    private BundledEffectsScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name,
                    Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        boolean reduce = config.reduceMotion;
        try {
            List<String> ids = context.computeOnClient(mc -> ShatterEffects.dataIds());
            List<String> messages = context.computeOnClient(mc -> FxEffects.messages());
            check.accept(ids.containsAll(BundledParams.ids()), name + ": all shipped effects load, got " + ids);
            check.accept(messages.stream().noneMatch(m -> m.contains("assets/shatter_gui/effects")),
                    name + ": no messages about shipped effects: " + messages);
            check.accept(context.computeOnClient(mc -> BundledParams.ids().stream().allMatch(ShatterEffects::isBundled)),
                    name + ": they are marked as the mod's own");

            config.openEffect = ShatterConfig.OPEN_NONE;
            for (String id : BundledParams.ids()) {
                close(context, check, shot, name + "_" + id, id, furnace, config);
            }

            // Reduce motion: the glitch's calm version.
            config.reduceMotion = true;
            close(context, check, shot, name + "_glitch_reduced", "glitch", furnace, config);
            config.reduceMotion = reduce;

            // Opening by gathering (flat GUI or Spatial GUI's plane; GatherScenario checks the frames of every effect).
            config.effect = "glass";
            for (String id : OPEN) {
                open(context, check, shot, name + "_open_" + id, id, furnace, config);
            }

            thematic(context, check, shot, name + "_thematic", furnace, config);

            // The Effects tab with a shipped effect selected, then its copy in the editor.
            config.openEffect = ShatterConfig.OPEN_NONE;
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> {
                ShatterConfigScreen screen = (ShatterConfigScreen) mc.screen;
                screen.selectTab(ShatterConfigScreen.Tab.EFFECTS);
                screen.selectEffect("frost");
            });
            context.waitTicks(25);
            shot.accept(name + "_effects_tab_frost");
            context.runOnClient(mc -> ((ShatterConfigScreen) mc.screen).cloneBundled("frost"));
            context.waitTicks(10);
            String doc = context.computeOnClient(mc -> {
                FxEditor editor = mc.screen instanceof ShatterConfigScreen s ? s.effectEditor()
                        : ((net.shattergui.editor.FxEditorScreen) mc.screen).editor();
                return editor.fileId() == null ? editor.docId() : "saved:" + editor.fileId();
            });
            check.accept("frost_copy".equals(doc), name + ": the copy opens unsaved as frost_copy, got " + doc);
            shot.accept(name + "_clone_in_editor");
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(3);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            config.reduceMotion = reduce;
        }
    }

    /** Thematic mode: a furnace burns; the player's change to the table; off again → the general effect. */
    private static void thematic(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot,
                                 String name, Supplier<Screen> furnace, ShatterConfig config) {
        boolean was = config.thematic;
        java.util.Map<String, String> themes = new java.util.LinkedHashMap<>(config.themes);
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
            config.thematic = true;
            config.themes.clear();
            check.accept("burn".equals(closeAndName(context, furnace)), name + ": a furnace burns in thematic mode");
            config.themes.put("furnaces", "soul_fire");
            check.accept("soul_fire".equals(closeAndName(context, furnace)), name + ": the player's choice for furnaces");
            config.themes.put("furnaces", net.shattergui.config.Themes.GENERAL);
            check.accept("glass".equals(closeAndName(context, furnace)), name + ": a rule set to the general effect");
            config.thematic = false;
            config.themes.clear();
            check.accept("glass".equals(closeAndName(context, furnace)), name + ": off by default: the general effect");
            // The table in the Screens tab.
            config.thematic = true;
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> ((ShatterConfigScreen) mc.screen).selectTab(ShatterConfigScreen.Tab.SCREENS));
            context.waitTicks(5);
            context.runOnClient(mc -> ((ShatterConfigScreen) mc.screen).scrollToEnd());
            context.waitTicks(15);
            shot.accept(name + "_screens_tab");
            context.runOnClient(mc -> mc.setScreen(null));
            waitUntilClear(context, 200);
        } finally {
            config.thematic = was;
            config.themes.clear();
            config.themes.putAll(themes);
        }
    }

    private static String closeAndName(ClientGameTestContext context, Supplier<Screen> furnace) {
        context.setScreen(furnace);
        context.waitTicks(5);
        context.runOnClient(mc -> mc.setScreen(null));
        context.waitTicks(2);
        String running = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                : ShardManager.batches().getLast().effect().id());
        waitUntilClear(context, 400);
        return running;
    }

    private static boolean waitUntilClear(ClientGameTestContext context, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (context.computeOnClient(mc -> ShardManager.waveCount() == 0)) {
                return true;
            }
            context.waitTick();
        }
        return false;
    }

    private static void close(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot,
                              String name, String id, Supplier<Screen> furnace, ShatterConfig config) {
        config.effect = id;
        context.setScreen(furnace);
        context.waitTicks(12);
        int duration = BundledParams.fileDuration(id);
        context.runOnClient(mc -> mc.setScreen(null));
        context.waitTicks(2);
        String running = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                : ShardManager.batches().getLast().effect().id());
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        int waited = 2;
        for (float f : new float[]{0.15f, 0.45f, 0.75f}) {
            int at = Math.max(waited + 1, Math.round(duration * f));
            context.waitTicks(at - waited);
            waited = at;
            shot.accept(name + "_t" + at);
        }
        boolean ended = waitUntilClear(context, duration + 260);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: ran {} with {} shards, ended={}", name, running, shards, ended);
        check.accept(id.equals(running) && shards > 0, name + ": must run (ran " + running + ", " + shards + " shards)");
        check.accept(ended, name + ": must end");
        check.accept(context.computeOnClient(mc -> FxSimulation.liveSpritesTotal()) == 0, name + ": particles released");
        context.waitTicks(3);
    }

    private static void open(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot,
                             String name, String id, Supplier<Screen> furnace, ShatterConfig config) {
        config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + id;
        int duration = BundledParams.fileDuration(id);
        context.setScreen(furnace);
        context.waitTicks(2);
        boolean hidden = context.computeOnClient(mc -> OpenAssemble.hides(mc.screen));
        String running = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                : ShardManager.batches().getLast().effect().id());
        int waited = 2;
        for (float f : new float[]{0.2f, 0.5f, 0.85f}) {
            int at = Math.max(waited + 1, Math.round(duration * f));
            context.waitTicks(at - waited);
            waited = at;
            shot.accept(name + "_t" + at);
        }
        context.waitTicks(duration + 10 - waited);
        boolean shown = context.computeOnClient(mc -> mc.screen != null && !OpenAssemble.hides(mc.screen));
        shot.accept(name + "_done");
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: hidden at first={}, ran {}, shown after={}", name, hidden, running, shown);
        check.accept(hidden, name + ": the screen is hidden while it gathers");
        check.accept(id.equals(running), name + ": the gathering runs " + id + " (ran " + running + ")");
        check.accept(shown, name + ": the screen is shown once the pieces have landed");
        // Closing it again works as usual (glass).
        context.runOnClient(mc -> mc.setScreen(null));
        waitUntilClear(context, 200);
        context.waitTicks(3);
    }
}
