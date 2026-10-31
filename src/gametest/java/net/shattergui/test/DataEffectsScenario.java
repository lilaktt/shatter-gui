package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.FxSimulation;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.shatter.ShardManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Schema-2 effects in the game: the four examples (one as the package written on first start, three from the test's
 * resource pack) and a test package run on a furnace with screenshots across each effect; a broken file is ignored
 * with a message naming the field; F3+T picks up a new effect with a config texture; a corrupt PNG is only a warning;
 * export as a code and import back; spam protection; every own particle is released afterwards.
 */
final class DataEffectsScenario {
    private static final List<String> EXAMPLES = List.of("mask_spiral", "mask_burn", "sprite_rain", "layer_combo", "packaged_rain");

    private DataEffectsScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name,
                    Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        Path pkg = FxEffects.PACKAGES.resolve("packaged_rain.shatterfx");
        Path reloadJson = FxEffects.EFFECTS.resolve("test_reload_fx.json");
        Path badJson = FxEffects.EFFECTS.resolve("test_badtex_fx.json");
        Path mask = FxEffects.TEXTURES.resolve("test_mask.png");
        Path badPng = FxEffects.TEXTURES.resolve("test_bad.png");
        Path imported = FxEffects.EFFECTS.resolve("layer_combo.json");
        try {
            // A package dropped into the folder, then the editor's reload.
            Files.createDirectories(FxEffects.PACKAGES);
            try (InputStream in = DataEffectsScenario.class.getResourceAsStream("/test_packages/packaged_rain.shatterfx")) {
                Files.write(pkg, in.readAllBytes());
            }
            context.runOnClient(mc -> FxEffects.reloadAll());
            List<String> ids = context.computeOnClient(mc -> ShatterEffects.dataIds());
            List<String> messages = context.computeOnClient(mc -> FxEffects.messages());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: schema-2 effects {}; messages {}", name, ids, messages);
            check.accept(ids.containsAll(EXAMPLES), name + ": all examples and the package must load, got " + ids);
            check.accept(!ids.contains("fx_broken"), name + ": a broken effect must be ignored");
            check.accept(messages.stream().anyMatch(m -> m.contains("fx_broken") && m.contains("layers[0].module")
                            && m.contains("appearance.color[1]")),
                    name + ": the error message must name the broken fields");
            check.accept(context.computeOnClient(mc -> ShatterEffects.ids().containsAll(EXAMPLES)),
                    name + ": schema-2 effects must be in the effect list (keybind, editor)");
            DataEffect spiral = context.computeOnClient(mc -> FxEffects.get("mask_spiral"));
            check.accept(spiral != null && spiral.loadedTextures().size() == 3,
                    name + ": the example package must load its 3 textures");

            config.openEffect = ShatterConfig.OPEN_NONE;
            for (String id : EXAMPLES) {
                playOnce(context, check, shot, name + "_" + id, id, furnace, config);
            }

            // F3+T: a new effect in the config folder with a texture from the config textures folder.
            Files.createDirectories(FxEffects.TEXTURES);
            try (InputStream in = DataEffectsScenario.class.getResourceAsStream(
                    "/assets/shatter_gui_test/shatter_gui/textures/burn_mask.png")) {
                Files.write(mask, in.readAllBytes());
            }
            Files.writeString(reloadJson, """
                    {"schema": 2, "id": "test_reload_fx", "duration": 30,
                     "fracture": {"mode": "grid", "count": 40},
                     "trigger": {"type": "mask", "texture": "test_mask", "spread": 0.6},
                     "layers": [{"module": "gravity", "params": {"strength": 0.01}}]}
                    """, StandardCharsets.UTF_8);
            Files.write(badPng, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25});
            Files.writeString(badJson, """
                    {"schema": 2, "id": "test_badtex_fx", "trigger": {"type": "mask", "texture": "test_bad"},
                     "layers": [{"module": "drag"}]}
                    """, StandardCharsets.UTF_8);
            context.runOnClient(Minecraft::reloadResourcePacks);
            context.waitFor(mc -> mc.getOverlay() == null, 600);
            context.waitTicks(5);
            DataEffect reloaded = context.computeOnClient(mc -> FxEffects.get("test_reload_fx"));
            check.accept(reloaded != null && reloaded.loadedTextures().contains("test_mask"),
                    name + ": F3+T must load a new effect with its config texture");
            List<String> afterReload = context.computeOnClient(mc -> FxEffects.messages());
            check.accept(context.computeOnClient(mc -> FxEffects.get("test_badtex_fx")) != null
                            && afterReload.stream().anyMatch(m -> m.contains("test_bad") && m.contains("not a PNG")),
                    name + ": a corrupt PNG is a warning, the effect still loads (mask falls back to noise)");
            playOnce(context, check, shot, name + "_test_badtex_fx", "test_badtex_fx", furnace, config);

            // Export as a code (no textures) and import it back (saved to the config folder, wins over the pack).
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            String code = context.computeOnClient(mc -> ((ShatterConfigScreen) mc.screen).exportData("layer_combo"));
            String noCode = context.computeOnClient(mc -> ((ShatterConfigScreen) mc.screen).exportData("mask_spiral"));
            check.accept(code != null && noCode == null, name + ": a code for effects without textures only");
            context.runOnClient(mc -> mc.keyboardHandler.setClipboard(code));
            context.runOnClient(mc -> ((ShatterConfigScreen) mc.screen).importClipboard());
            check.accept(Files.exists(imported), name + ": import must save effects/layer_combo.json");
            context.runOnClient(mc -> ((ShatterConfigScreen) mc.screen).selectTab(ShatterConfigScreen.Tab.CUSTOM));
            context.waitTicks(10);
            shot.accept(name + "_editor_custom");
            context.runOnClient(mc -> {
                ShatterConfigScreen editor = (ShatterConfigScreen) mc.screen;
                editor.selectTab(ShatterConfigScreen.Tab.EFFECTS);
                editor.selectEffect("mask_spiral");
            });
            for (int t : new int[]{10, 25, 40}) {
                context.waitTicks(t == 10 ? 10 : 15);
                shot.accept(name + "_editor_preview_spiral_t" + t);
            }
            context.runOnClient(mc -> ((ShatterConfigScreen) mc.screen).selectEffect("sprite_rain"));
            context.waitTicks(30);
            shot.accept(name + "_editor_preview_rain");
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(3);

            // Spam: many rains at once stay bounded and leave nothing behind.
            config.effect = "sprite_rain";
            for (int i = 0; i < 8; i++) {
                context.setScreen(furnace);
                context.waitTicks(2);
                context.runOnClient(mc -> mc.setScreen(null));
                context.waitTicks(2);
            }
            // Hurried waves fade out within 6 ticks; then at most 4 run at once.
            context.waitTicks(7);
            int waves = context.computeOnClient(mc -> ShardManager.waveCount());
            int sprites = context.computeOnClient(mc -> FxSimulation.liveSpritesTotal());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: spam → {} waves, {} sprites", name, waves, sprites);
            check.accept(waves <= 4, name + ": spam protection keeps waves bounded, " + waves);
            waitUntilClear(context, 600);
            check.accept(context.computeOnClient(mc -> FxSimulation.liveSpritesTotal()) == 0,
                    name + ": every own particle must be released");
        } catch (IOException e) {
            check.accept(false, name + ": " + e);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            for (Path file : new Path[]{pkg, reloadJson, badJson, mask, badPng, imported}) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // Left over in the test run folder only.
                }
            }
            context.runOnClient(mc -> FxEffects.reloadAll());
        }
    }

    /** Waits until no wave is left (true) or the time is up (false). */
    private static boolean waitUntilClear(ClientGameTestContext context, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (context.computeOnClient(mc -> ShardManager.waveCount() == 0)) {
                return true;
            }
            context.waitTick();
        }
        return false;
    }

    /** Closes a furnace with the effect; screenshots across it; checks it ran and ended cleanly. */
    private static void playOnce(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot,
                                 String name, String id, Supplier<Screen> furnace, ShatterConfig config) {
        config.effect = id;
        context.setScreen(furnace);
        context.waitTicks(15);
        int duration = context.computeOnClient(mc -> FxEffects.get(id).def.duration);
        context.runOnClient(mc -> mc.setScreen(null));
        context.waitTicks(2);
        String running = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                : ShardManager.batches().getLast().effect().id());
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        int waited = 2;
        int peakSprites = 0;
        for (float f : new float[]{0.1f, 0.3f, 0.5f, 0.7f, 0.9f}) {
            int at = Math.max(waited + 1, Math.round(duration * f));
            context.waitTicks(at - waited);
            waited = at;
            peakSprites = Math.max(peakSprites, context.computeOnClient(mc -> FxSimulation.liveSpritesTotal()));
            shot.accept(name + "_t" + at);
        }
        boolean ended = waitUntilClear(context, duration + 260);
        int leftSprites = context.computeOnClient(mc -> FxSimulation.liveSpritesTotal());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: ran {} with {} shards, sprite peak {}, ended={}", name, running,
                shards, peakSprites, ended);
        check.accept(id.equals(running) && shards > 0, name + ": must run (ran " + running + ", " + shards + " shards)");
        if (id.contains("rain")) {
            check.accept(peakSprites > 0, name + ": own raindrop particles must appear");
        }
        check.accept(ended, name + ": must end within its duration plus the longest particle life");
        check.accept(leftSprites == 0, name + ": own particles released at the end (" + leftSprites + ")");
        context.waitTicks(5);
    }
}
