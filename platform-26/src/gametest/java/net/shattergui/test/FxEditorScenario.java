package net.shattergui.test;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.editor.EffectFiles;
import net.shattergui.editor.FxEditor;
import net.shattergui.editor.FxEditorScreen;
import net.shattergui.editor.Selection;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.shatter.ShardManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The visual effect editor end to end: two own textures go into the textures folder; a new effect is built in the
 * editor (a mask-texture front, an attract layer with a keyframed curve, a spin layer, an overlay with the texture,
 * an own-sprite emitter, a sound), saved, played by closing a furnace, exported as a .shatterfx, deleted (with its
 * textures) and imported back. On the way: undo/redo, Space, the tour, every panel and dialog as a screenshot, the
 * editor at GUI scales 2–4 (full screen) and inside the settings.
 */
final class FxEditorScenario {
    private static final String ID = "test_editor_fx";
    private static final String MASK = "test_editor_mask";
    private static final String SPRITE = "test_editor_drop";

    private FxEditorScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name,
                    Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        Path mask = FxEffects.TEXTURES.resolve(MASK + ".png");
        Path sprite = FxEffects.TEXTURES.resolve(SPRITE + ".png");
        Path file = FxEffects.EFFECTS.resolve(ID + ".json");
        Path exported = FxEffects.EXPORTS.resolve(ID + ".shatterfx");
        try {
            Files.createDirectories(FxEffects.TEXTURES);
            copy("/assets/shatter_gui_test/shatter_gui/textures/burn_mask.png", mask);
            copy("/assets/shatter_gui_test/shatter_gui/textures/raindrop.png", sprite);
            Files.deleteIfExists(file);
            Files.deleteIfExists(exported);

            EditorScenario.setScale(context, 2);
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            // Inside the settings ("Custom" tab).
            context.runOnClient(mc -> {
                ShatterConfigScreen settings = EditorScenario.editor(mc);
                settings.selectTab(ShatterConfigScreen.Tab.CUSTOM);
                settings.effectEditor();
            });
            context.waitTicks(10);
            // The tour: open it (it may have been seen already) and go through it.
            context.runOnClient(mc -> {
                FxEditor editor = EditorScenario.editor(mc).effectEditor();
                if (editor.tourStep() < 0) {
                    editor.startTour();
                }
            });
            for (int step = 1; step <= 4; step++) {
                context.waitTicks(8);
                shot.accept(name + "_tour_" + step);
                if (step < 4) {
                    context.getInput().pressKey(InputConstants.KEY_RETURN);
                }
            }
            context.runOnClient(mc -> EditorScenario.editor(mc).effectEditor().tourNext());
            context.waitTicks(3);
            check.accept(context.computeOnClient(mc -> EditorScenario.editor(mc).effectEditor().tourStep()) < 0,
                    name + ": the tour closes after its last step");
            shot.accept(name + "_in_settings");

            // Full screen.
            context.runOnClient(mc -> EditorScenario.editor(mc).openEditorFullscreen());
            context.waitFor(mc -> mc.screen instanceof FxEditorScreen, 100);
            context.waitTicks(5);

            // A new effect, built through the editor's own actions.
            context.runOnClient(mc -> {
                FxEditor editor = editor(mc);
                editor.newEffect("empty");
                editor.doc.set("id", ID);
                editor.doc.set("name", "Editor test");
                editor.doc.set("duration", 50);
                editor.doc.set("fracture.mode", "voronoi");
                editor.doc.set("fracture.count", 90);
                editor.rescanTextures();
                editor.useTexture(MASK, "trigger");
                int attract = editor.addLayer(FxDef.Module.ATTRACT);
                String p = "layers[" + attract + "].params";
                editor.doc.set(p + ".swirl", 2f);
                editor.doc.set(p + ".to", "center");
                editor.doc.set(p + ".strength", JsonParser.parseString(
                        "{\"keys\": [[0, 0.004], [0.5, 0.02], [1, 0.03]], \"ease\": \"smooth\"}"));
                editor.addLayer(FxDef.Module.SPIN);
                editor.addLayer(FxDef.Module.SCALE);
                editor.doc.set("layers[2].params.value", JsonParser.parseString("{\"curve\": \"easeInCubic\", \"from\": 1, \"to\": 0}"));
                editor.useTexture(MASK, "overlay");
                editor.select(Selection.EFFECT);
                editor.useTexture(SPRITE, "sprite");
                editor.doc.set("emitters[0].rate", 3f);
                editor.doc.set("emitters[0].zone", "front");
                editor.doc.set("emitters[0].size", 0.08f);
                editor.addSound();
                editor.doc.set("sounds[0].sound", "minecraft:block.amethyst_block.chime");
                editor.select(new Selection(Selection.Kind.LAYER, attract));
                editor.showParamCurve(attract, "strength");
            });
            context.waitTicks(20);
            String described = context.computeOnClient(mc -> editor(mc).describe());
            String problems = context.computeOnClient(mc -> editor(mc).problems().toString());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: built {}; problems {}", name, described, problems);
            boolean noErrors = context.computeOnClient(mc -> editor(mc).problems().stream().noneMatch(FxProblems.Problem::error));
            check.accept(noErrors, name + ": the built effect must have no errors: " + problems);
            check.accept(context.computeOnClient(mc -> editor(mc).doc.result().def().textures.size()) == 2,
                    name + ": the effect uses the two own textures");

            // Screenshots of the panels.
            for (Selection s : new Selection[]{Selection.EFFECT, Selection.APPEARANCE, Selection.EDGE,
                    new Selection(Selection.Kind.LAYER, 0), new Selection(Selection.Kind.EMITTER, 0),
                    new Selection(Selection.Kind.SOUND, 0)}) {
                context.runOnClient(mc -> editor(mc).select(s));
                context.waitTicks(8);
                shot.accept(name + "_panel_" + s.kind().name().toLowerCase(java.util.Locale.ROOT));
            }
            context.runOnClient(mc -> {
                editor(mc).showLeftTab(1);
                editor(mc).chooseTexture(MASK);
            });
            context.waitTicks(8);
            shot.accept(name + "_panel_textures");
            context.runOnClient(mc -> editor(mc).showLeftTab(0));
            for (String dialog : new String[]{"catalog", "gallery", "import", "menu"}) {
                context.runOnClient(mc -> {
                    FxEditor editor = editor(mc);
                    switch (dialog) {
                        case "catalog" -> editor.openCatalog();
                        case "gallery" -> editor.openGallery();
                        case "import" -> editor.openImport();
                        default -> editor.openEffectsMenu();
                    }
                });
                context.waitTicks(12);
                shot.accept(name + "_dialog_" + dialog);
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.waitTicks(3);
            }
            check.accept(context.computeOnClient(mc -> mc.screen instanceof FxEditorScreen s && !s.hasOverlay()),
                    name + ": Esc closes each dialog and keeps the editor");

            // Undo/redo and Space.
            context.runOnClient(mc -> editor(mc).doc.breakMerge());
            context.runOnClient(mc -> editor(mc).doc.set("duration", 77));
            context.runOnClient(mc -> editor(mc).undo());
            float undone = context.computeOnClient(mc -> editor(mc).doc.num("duration", 0));
            context.runOnClient(mc -> editor(mc).redo());
            float redone = context.computeOnClient(mc -> editor(mc).doc.num("duration", 0));
            context.runOnClient(mc -> editor(mc).undo());
            check.accept(undone == 50 && redone == 77, name + ": undo/redo (" + undone + ", " + redone + ")");
            boolean playing = context.computeOnClient(mc -> editor(mc).previewState().playing());
            context.runOnClient(mc -> ((FxEditorScreen) mc.screen).setFocus(null));
            context.getInput().pressKey(InputConstants.KEY_SPACE);
            context.waitTicks(2);
            boolean after = context.computeOnClient(mc -> editor(mc).previewState().playing());
            check.accept(playing != after, name + ": Space pauses and plays the preview");
            context.runOnClient(mc -> editor(mc).previewState().setPlaying(true));

            // Nonsense typed into fields never breaks anything.
            context.runOnClient(mc -> {
                FxEditor editor = editor(mc);
                editor.doc.breakMerge();
                editor.doc.set("layers[0].params.swirl", "abc");
                editor.doc.set("fracture.count", -5);
                editor.doc.set("emitters[0].size", JsonParser.parseString("{\"keys\": [[0.9, 1], [0.1, 2]]}"));
            });
            context.waitTicks(10);
            shot.accept(name + "_errors_shown");
            int errors = context.computeOnClient(mc -> editor(mc).errorCount());
            check.accept(errors > 0, name + ": nonsense is reported as errors (" + errors + ")");
            context.runOnClient(mc -> {
                for (int i = 0; i < 3; i++) {
                    editor(mc).undo();
                }
            });
            context.waitTicks(3);
            check.accept(context.computeOnClient(mc -> editor(mc).errorCount()) == 0, name + ": undo removes the errors");

            // GUI scales, full screen.
            for (int s : new int[]{2, 3, 4}) {
                EditorScenario.setScale(context, s);
                context.waitTicks(10);
                shot.accept(name + "_scale" + s);
            }
            EditorScenario.setScale(context, 2);

            // Save: written atomically, loaded by the game.
            boolean saved = context.computeOnClient(mc -> editor(mc).save());
            context.waitTicks(2);
            check.accept(saved && Files.exists(file), name + ": save writes effects/" + ID + ".json");
            check.accept(context.computeOnClient(mc -> FxEffects.get(ID)) != null, name + ": the saved effect is loaded");
            check.accept(!hasTempFiles(FxEffects.EFFECTS), name + ": no temporary files are left");
            shot.accept(name + "_saved");

            // Close a furnace with it.
            context.runOnClient(mc -> ((FxEditorScreen) mc.screen).back());
            context.waitTicks(2);
            context.runOnClient(mc -> mc.screen.onClose());
            context.waitTicks(3);
            config.effect = ID;
            config.openEffect = ShatterConfig.OPEN_NONE;
            context.setScreen(furnace);
            context.waitTicks(10);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(2);
            String running = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                    : ShardManager.batches().getLast().effect().id());
            for (int t : new int[]{10, 25, 40}) {
                context.waitTicks(t == 10 ? 8 : 15);
                shot.accept(name + "_furnace_t" + t);
            }
            check.accept(ID.equals(running), name + ": closing the furnace plays the editor's effect (ran " + running + ")");
            waitUntilClear(context);

            // Export, delete (with the textures), import back.
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> {
                EditorScenario.editor(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM);
                EditorScenario.editor(mc).effectEditor().open(ID);
            });
            context.waitTicks(5);
            Path written = context.computeOnClient(mc -> EditorScenario.editor(mc).effectEditor().exportPackage());
            check.accept(written != null && Files.exists(exported), name + ": export writes exports/" + ID + ".shatterfx");
            context.runOnClient(mc -> EditorScenario.editor(mc).effectEditor().deleteEffect(ID));
            Files.deleteIfExists(mask);
            Files.deleteIfExists(sprite);
            context.waitTicks(3);
            check.accept(!Files.exists(file) && context.computeOnClient(mc -> FxEffects.get(ID)) == null,
                    name + ": delete removes the file and the loaded effect");
            EffectFiles.ImportResult imported = context.computeOnClient(mc ->
                    EditorScenario.editor(mc).effectEditor().importPackage(exported));
            context.waitTicks(5);
            shot.accept(name + "_imported");
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: import {} {}", name, imported.id(), imported.messages());
            check.accept(imported.ok() && ID.equals(imported.id()) && Files.exists(file) && Files.exists(mask) && Files.exists(sprite),
                    name + ": import brings back the effect and both textures");
            check.accept(context.computeOnClient(mc -> FxEffects.get(ID)) != null, name + ": the imported effect is loaded");
            check.accept(context.computeOnClient(mc -> ID.equals(EditorScenario.editor(mc).effectEditor().fileId())),
                    name + ": the imported effect is open in the editor");
            context.runOnClient(mc -> mc.screen.onClose());
            context.waitTicks(3);
        } catch (IOException e) {
            check.accept(false, name + ": " + e);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            for (Path p : new Path[]{file, exported, mask, sprite}) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Left over in the test run folder only.
                }
            }
            context.runOnClient(mc -> {
                if (mc.screen != null) {
                    mc.setScreen(null);
                }
                FxEffects.reloadAll();
            });
            EditorScenario.setScale(context, scale);
        }
    }

    private static FxEditor editor(Minecraft mc) {
        if (mc.screen instanceof FxEditorScreen screen) {
            return screen.editor();
        }
        return EditorScenario.editor(mc).effectEditor();
    }

    private static void copy(String resource, Path to) throws IOException {
        try (InputStream in = FxEditorScenario.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing test resource " + resource);
            }
            Files.write(to, in.readAllBytes());
        }
    }

    private static boolean hasTempFiles(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.anyMatch(f -> f.getFileName().toString().endsWith(".tmp"));
        }
    }

    private static void waitUntilClear(ClientGameTestContext context) {
        for (int i = 0; i < 400; i++) {
            if (context.computeOnClient(mc -> ShardManager.waveCount() == 0)) {
                return;
            }
            context.waitTick();
        }
    }
}
