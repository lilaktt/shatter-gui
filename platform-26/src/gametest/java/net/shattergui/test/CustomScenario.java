package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.custom.CustomEffectDef;
import net.shattergui.custom.CustomEffects;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Custom effects: the example file written on first start, resource-pack effects (with bad entries skipped, a broken
 * file ignored, the config winning on the same id), reload with the resources (F3+T), and in the editor (Ukrainian):
 * create from a built-in effect, rename, change a parameter, export to the clipboard, delete, import back, a bad code
 * rejected, preview, Save, then closing a real screen with the new effect. The effects folder is restored afterwards.
 */
final class CustomScenario {
    private static final String EXAMPLE = "example_ember_swirl";

    private CustomScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name,
                    Supplier<Screen> furnace) {
        ShatterConfig original = ShatterConfig.get();
        Map<Path, String> folder = snapshot();
        EditorScenario.setLanguage(context, "uk_ua");
        try {
            // Loading.
            check.accept(Files.exists(CustomEffects.DIRECTORY.resolve(EXAMPLE + ".json")),
                    name + ": the example file must be written on first start");
            boolean example = context.computeOnClient(mc -> ShatterEffects.ids().contains(EXAMPLE));
            String exampleName = context.computeOnClient(mc -> ShatterEffects.displayName(EXAMPLE).getString());
            boolean pack = context.computeOnClient(mc -> ShatterEffects.ids().contains("pack_pulse"));
            boolean broken = context.computeOnClient(mc -> ShatterEffects.ids().stream().anyMatch(id -> id.contains("roken")));
            CustomEffectDef pulse = CustomEffects.fromPacks().stream().filter(d -> d.id().equals("pack_pulse")).findFirst()
                    .orElse(null);
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: ids={} example name='{}' pack pulse={}", name,
                    context.computeOnClient(mc -> ShatterEffects.ids()), exampleName,
                    pulse == null ? null : pulse.toJsonString().replace('\n', ' '));
            check.accept(example && pack && !broken, name + ": example and pack effects loaded, broken one ignored");
            check.accept(!exampleName.startsWith("PACK"), name + ": the config file must win over a resource pack, name "
                    + exampleName);
            check.accept(pulse != null && pulse.particles().size() == 1 && pulse.sounds().size() == 1
                            && pulse.get(Params.DISSOLVE_DURATION) == 12,
                    name + ": unknown particle/sound/parameter skipped, the rest kept");

            // F3+T: a file added to the folder appears after the resource reload.
            Files.writeString(CustomEffects.DIRECTORY.resolve("test_reload.json"),
                    "{\"schema\":1,\"id\":\"test_reload\",\"name\":\"Reload test\",\"base\":\"glass\",\"params\":{\"gravity\":0.2}}",
                    StandardCharsets.UTF_8);
            Files.writeString(CustomEffects.DIRECTORY.resolve("test_garbage.json"), "{ not json", StandardCharsets.UTF_8);
            context.runOnClient(Minecraft::reloadResourcePacks);
            context.waitFor(mc -> mc.getOverlay() == null, 600);
            context.waitTicks(5);
            boolean reloaded = context.computeOnClient(mc -> ShatterEffects.ids().contains("test_reload"));
            check.accept(reloaded, name + ": resource reload (F3+T) must pick up a new file");
            Files.deleteIfExists(CustomEffects.DIRECTORY.resolve("test_reload.json"));
            Files.deleteIfExists(CustomEffects.DIRECTORY.resolve("test_garbage.json"));
            context.runOnClient(mc -> CustomEffects.reloadConfig());

            // Editor: the Custom tab with the example.
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> editor(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM));
            context.waitTicks(2);
            shot.accept(name + "_tab_custom_example");

            // Create from the vortex, rename, change a parameter.
            String created = context.computeOnClient(mc -> {
                ShatterConfigScreen editor = editor(mc);
                editor.setCloneFrom("vortex");
                CustomEffectDef def = editor.createCustom("vortex");
                def.rename("Мій фіолетовий вихор");
                def.set(Params.VORTEX_TURNS, 4f);
                def.set(Params.VORTEX_TARGET, "player");
                editor.selectCustom(def.id());
                return def.id();
            });
            context.waitTicks(2);
            shot.accept(name + "_created");

            // Export, delete, import back.
            String code = context.computeOnClient(mc -> editor(mc).exportSelected());
            String clipboard = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
            context.waitTicks(2);
            shot.accept(name + "_exported");
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: exported {} as {} characters: {}", name, created,
                    code == null ? -1 : code.length(), code);
            check.accept(code != null && code.equals(clipboard), name + ": export must put the code into the clipboard");
            context.runOnClient(mc -> editor(mc).deleteSelected());
            context.waitTicks(2);
            boolean deleted = context.computeOnClient(mc -> editor(mc).customs().stream().noneMatch(d -> d.id().equals(created)));
            CustomEffectDef imported = context.computeOnClient(mc -> editor(mc).importClipboard());
            context.waitTicks(2);
            shot.accept(name + "_imported");
            check.accept(deleted && imported != null && imported.name().equals("Мій фіолетовий вихор")
                            && imported.get(Params.VORTEX_TURNS) == 4f && "player".equals(imported.get(Params.VORTEX_TARGET)),
                    name + ": import must give back the exported effect (" + (imported == null ? null : imported.toJsonString()) + ")");

            // A bad code: rejected with a message, nothing added.
            int before = context.computeOnClient(mc -> editor(mc).customs().size());
            context.runOnClient(mc -> mc.keyboardHandler.setClipboard("definitely not an effect code"));
            CustomEffectDef bad = context.computeOnClient(mc -> editor(mc).importClipboard());
            context.runOnClient(mc -> mc.keyboardHandler.setClipboard("A".repeat(CustomEffectDef.MAX_CODE_LENGTH + 10)));
            CustomEffectDef huge = context.computeOnClient(mc -> editor(mc).importClipboard());
            context.waitTicks(2);
            shot.accept(name + "_import_rejected");
            int after = context.computeOnClient(mc -> editor(mc).customs().size());
            String status = context.computeOnClient(mc -> editor(mc).status().getString());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: bad import status '{}'", name, status);
            check.accept(bad == null && huge == null && after == before, name + ": bad codes must be rejected");

            // Preview the unsaved effect.
            String id = imported != null ? imported.id() : created;
            context.runOnClient(mc -> {
                editor(mc).selectCustom(id);
                editor(mc).preview();
            });
            context.waitTicks(8);
            shot.accept(name + "_preview_t8");
            String previewed = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                    : ShardManager.batches().getLast().effect().id());
            context.waitTicks(8);
            shot.accept(name + "_preview_t16");
            check.accept(id.equals(previewed), name + ": the preview must play the unsaved custom effect, played " + previewed);
            context.waitFor(mc -> mc.screen instanceof ShatterConfigScreen, 200);
            context.waitTicks(2);

            // Save: written to the folder and selectable everywhere; choose it as the close effect.
            context.runOnClient(mc -> {
                editor(mc).working().effect = id;
                editor(mc).save();
            });
            context.waitTicks(3);
            boolean saved = Files.exists(CustomEffects.DIRECTORY.resolve(id + ".json"));
            boolean registered = context.computeOnClient(mc -> ShatterEffects.ids().contains(id));
            String chosen = context.computeOnClient(mc -> ShatterConfig.get().effect);
            check.accept(saved && registered && id.equals(chosen),
                    name + ": Save must write and register the effect (file=" + saved + ", registered=" + registered
                            + ", chosen=" + chosen + ")");

            // A real close with it.
            context.setScreen(furnace);
            context.waitTicks(15);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(4);
            String closed = context.computeOnClient(mc -> ShardManager.batches().isEmpty() ? "none"
                    : ShardManager.batches().getLast().effect().id());
            shot.accept(name + "_close_t4");
            context.waitTicks(10);
            shot.accept(name + "_close_t14");
            check.accept(id.equals(closed), name + ": closing must use the custom effect, used " + closed);
            context.waitTicks(40);
        } catch (IOException e) {
            check.accept(false, name + ": " + e);
        } finally {
            context.runOnClient(mc -> ShatterConfig.replace(original));
            restore(folder);
            context.runOnClient(mc -> CustomEffects.reloadConfig());
            EditorScenario.setLanguage(context, "en_us");
        }
    }

    private static ShatterConfigScreen editor(Minecraft mc) {
        if (!(mc.screen instanceof ShatterConfigScreen screen)) {
            throw new IllegalStateException("editor not open: " + mc.screen);
        }
        return screen;
    }

    private static Map<Path, String> snapshot() {
        Map<Path, String> files = new HashMap<>();
        try (var list = Files.list(CustomEffects.DIRECTORY)) {
            for (Path file : list.toList()) {
                files.put(file, Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return files;
    }

    private static void restore(Map<Path, String> files) {
        try (var list = Files.list(CustomEffects.DIRECTORY)) {
            for (Path file : list.toList()) {
                if (!files.containsKey(file)) {
                    Files.delete(file);
                }
            }
            for (Map.Entry<Path, String> entry : files.entrySet()) {
                Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}