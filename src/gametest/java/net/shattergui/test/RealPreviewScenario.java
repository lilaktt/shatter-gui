package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.editor.FxEditor;
import net.shattergui.shatter.ShardManager;
import net.shattergui.ui.preview.RealPreview;
import net.shattergui.ui.preview.SampleImage;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The editor's real preview: each vanilla screen is drawn off-screen into a texture the preview breaks; making them
 * opens nothing (Minecraft.screen, the player's open menu, the open/close effects and waves stay as they were); in
 * creative the inventory stays a sample; a screen that throws switches to the simple samples with a notice; the quick
 * switch turns it back on; closing the editor frees every texture.
 */
final class RealPreviewScenario {
    private RealPreviewScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    Consumer<String> shot, String name) {
        singleplayer.getServer().runCommand("gamemode survival @p");
        singleplayer.getServer().runCommand("give @p minecraft:diamond 7");
        singleplayer.getServer().runCommand("give @p minecraft:oak_planks 12");
        context.waitTicks(5);
        context.setScreen(() -> new ShatterConfigScreen(null));
        context.waitTicks(3);
        context.runOnClient(mc -> {
            ShatterConfigScreen s = EditorScenario.editor(mc);
            s.selectTab(ShatterConfigScreen.Tab.CUSTOM);
            s.effectEditor().closeTour();
            // At GUI scale 4 the editor only fits on the whole screen.
            s.openEditorFullscreen();
        });
        context.waitTicks(3);
        context.runOnClient(mc -> editor(mc).closeTour());
        context.waitTicks(3);
        Screen editorScreen = context.computeOnClient(mc -> mc.screen);
        Object menuBefore = context.computeOnClient(mc -> mc.player.containerMenu);
        int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        int waves = context.computeOnClient(mc -> ShardManager.waveCount());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: start {}", name, context.computeOnClient(mc -> RealPreview.describe()));
        check.accept(context.computeOnClient(mc -> RealPreview.enabled()), name + ": the real preview is on by default");

        for (SampleImage.Kind kind : SampleImage.Kind.values()) {
            context.runOnClient(mc -> editor(mc).setSample(kind));
            boolean ready = false;
            for (int i = 0; i < 40 && !ready; i++) {
                context.waitTick();
                ready = context.computeOnClient(mc -> {
                    SampleImage.Image image = SampleImage.image(kind);
                    return image.premultiplied();
                });
            }
            context.runOnClient(mc -> {
                editor(mc).previewState().setPlaying(false);
                editor(mc).previewState().seek(0);
            });
            context.waitTicks(3);
            shot.accept(name + "_" + kind.id() + "_t0");
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} {}", name, kind.id(), context.computeOnClient(mc -> RealPreview.describe()));
            context.runOnClient(mc -> editor(mc).previewState().seek(12));
            context.waitTicks(3);
            shot.accept(name + "_" + kind.id());
            context.runOnClient(mc -> editor(mc).previewState().setPlaying(true));
            check.accept(ready, name + ": the real " + kind.id() + " must be drawn into a texture ("
                    + context.computeOnClient(mc -> RealPreview.describe()) + ")");
        }
        check.accept(context.computeOnClient(mc -> mc.screen) == editorScreen,
                name + ": Minecraft.screen must stay the editor");
        check.accept(context.computeOnClient(mc -> mc.player.containerMenu) == menuBefore,
                name + ": the player's open menu must not change");
        check.accept(context.computeOnClient(mc -> ShatterProfiler.openCount()) == opens
                        && context.computeOnClient(mc -> ShatterProfiler.closeCount()) == closes
                        && context.computeOnClient(mc -> ShardManager.waveCount()) == waves,
                name + ": the preview screens must not start open or close effects");
        int textures = context.computeOnClient(mc -> RealPreview.texturesInMemory());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} preview textures in memory", name, textures);

        // Creative: the inventory screen would turn into the creative one; it stays the sample, nothing changes.
        singleplayer.getServer().runCommand("gamemode creative @p");
        context.waitTicks(5);
        context.runOnClient(mc -> {
            RealPreview.release();
            editor(mc).setSample(SampleImage.Kind.INVENTORY);
        });
        context.waitTicks(20);
        check.accept(context.computeOnClient(mc -> !SampleImage.image(SampleImage.Kind.INVENTORY).premultiplied()),
                name + ": in creative the inventory must stay the simple sample");
        check.accept(context.computeOnClient(mc -> mc.screen) == editorScreen
                        && context.computeOnClient(mc -> mc.player.containerMenu) == menuBefore,
                name + ": creative must not open the creative inventory or change the open menu");
        shot.accept(name + "_creative_inventory");
        singleplayer.getServer().runCommand("gamemode survival @p");
        context.waitTicks(5);

        // A screen that throws: simple samples, one notice, the editor keeps working.
        context.runOnClient(mc -> {
            RealPreview.release();
            RealPreview.breakNextForTests();
            editor(mc).setSample(SampleImage.Kind.CHEST);
        });
        context.waitTicks(10);
        check.accept(context.computeOnClient(mc -> !RealPreview.enabled() && RealPreview.notice()),
                name + ": an error must switch to the simple samples and show the notice");
        check.accept(context.computeOnClient(mc -> !SampleImage.image(SampleImage.Kind.CHEST).premultiplied()),
                name + ": after the error the sample is drawn");
        shot.accept(name + "_failed_notice");
        check.accept(context.computeOnClient(mc -> mc.screen) == editorScreen, name + ": the editor must stay open");

        // The quick switch turns it back on.
        context.runOnClient(mc -> RealPreview.toggleQuick());
        boolean back = false;
        for (int i = 0; i < 40 && !back; i++) {
            context.waitTick();
            back = context.computeOnClient(mc -> SampleImage.image(SampleImage.Kind.CHEST).premultiplied());
        }
        check.accept(back, name + ": the quick switch must bring the real preview back");
        context.runOnClient(mc -> RealPreview.toggleQuick());
        check.accept(context.computeOnClient(mc -> !RealPreview.enabled()
                        && !SampleImage.image(SampleImage.Kind.CHEST).premultiplied()),
                name + ": the quick switch must turn it off");
        context.runOnClient(mc -> RealPreview.toggleQuick());

        // Closing the editor (full screen → settings → world) frees the pictures.
        context.runOnClient(mc -> mc.screen.onClose());
        context.waitTicks(3);
        context.runOnClient(mc -> mc.screen.onClose());
        context.waitTicks(5);
        int left = context.computeOnClient(mc -> RealPreview.texturesInMemory());
        check.accept(left == 0, name + ": closing the editor must free the preview textures, " + left + " left");
        check.accept(context.computeOnClient(mc -> mc.screen == null), name + ": the editor closed normally");
        context.waitTicks(80);
    }

    private static FxEditor editor(net.minecraft.client.Minecraft mc) {
        return mc.screen instanceof net.shattergui.editor.FxEditorScreen s ? s.editor() : EditorScenario.editor(mc).effectEditor();
    }
}
