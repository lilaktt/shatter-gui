package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.editor.FxEditor;
import net.shattergui.editor.FxEditorScreen;
import net.shattergui.editor.Selection;
import net.shattergui.ui.UiFailures;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Screenshots of every settings tab and every part of the effect editor at GUI scales 1–4 (1920×1080), for comparing
 * the look before and after a change: {@code -PtestOnly=shots -PshotsLabel=before}. Names:
 * {@code <label>_s<scale>_<tab or editor part>}.
 */
final class EditorShotsScenario {
    private EditorShotsScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String prefix) {
        String label = prefix + System.getProperty("shatter.test.shotsLabel", "shots");
        int failuresBefore = UiFailures.count();
        int scaleBefore = context.computeOnClient(mc -> mc.options.guiScale().get());
        context.getInput().setCursorPos(2, 2);
        try {
            for (int scale = 1; scale <= 4; scale++) {
                EditorScenario.setScale(context, scale);
                context.setScreen(() -> new ShatterConfigScreen(null));
                context.waitTicks(4);
                for (ShatterConfigScreen.Tab tab : ShatterConfigScreen.Tab.values()) {
                    context.runOnClient(mc -> EditorScenario.editor(mc).selectTab(tab));
                    context.waitTicks(8);
                    if (tab == ShatterConfigScreen.Tab.CUSTOM) {
                        context.runOnClient(mc -> {
                            FxEditor editor = EditorScenario.editor(mc).effectEditor();
                            editor.closeTour();
                            editor.newEffect("burn");
                            editor.select(new Selection(Selection.Kind.LAYER, 1));
                        });
                        context.waitTicks(8);
                    }
                    shot.accept(label + "_s" + scale + "_" + tab.name().toLowerCase(java.util.Locale.ROOT));
                }
                // The editor on the whole screen: each part.
                context.runOnClient(mc -> EditorScenario.editor(mc).openEditorFullscreen());
                context.waitFor(mc -> mc.screen instanceof FxEditorScreen, 100);
                Selection[] parts = {Selection.EFFECT, Selection.APPEARANCE, new Selection(Selection.Kind.LAYER, 1),
                        new Selection(Selection.Kind.EMITTER, 0), new Selection(Selection.Kind.SOUND, 0)};
                for (Selection part : parts) {
                    context.runOnClient(mc -> editor(mc).select(part));
                    context.waitTicks(8);
                    shot.accept(label + "_s" + scale + "_editor_" + part.kind().name().toLowerCase(java.util.Locale.ROOT));
                }
                context.runOnClient(mc -> editor(mc).showLeftTab(1));
                context.waitTicks(6);
                shot.accept(label + "_s" + scale + "_editor_textures");
                context.runOnClient(mc -> {
                    editor(mc).showLeftTab(0);
                    editor(mc).openCatalog();
                });
                context.waitTicks(10);
                shot.accept(label + "_s" + scale + "_editor_catalog");
                context.runOnClient(mc -> {
                    editor(mc).closeTour();
                    ((FxEditorScreen) mc.screen).back();
                });
                context.waitTicks(2);
                context.runOnClient(mc -> mc.screen.onClose());
                context.waitTicks(2);
            }
        } finally {
            EditorScenario.setScale(context, scaleBefore);
            context.runOnClient(mc -> {
                if (mc.screen != null) {
                    mc.setScreen(null);
                }
            });
        }
        int failures = UiFailures.count() - failuresBefore;
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: UI failures {} {}", label, failures, UiFailures.kinds());
        check.accept(failures == 0, label + ": no widget may fail (" + UiFailures.kinds() + ")");
    }

    private static FxEditor editor(Minecraft mc) {
        return ((FxEditorScreen) mc.screen).editor();
    }
}
