package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.editor.FxEditor;
import net.shattergui.editor.FxEditorScreen;
import net.shattergui.editor.Selection;
import net.shattergui.fx.FxDef;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Slider;
import net.shattergui.ui.Swatch;
import net.shattergui.ui.TabNav;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Toggle;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Every control the player can use has a tooltip — its own or its row's (what {@link UiScreen#tooltipOwner} shows):
 * on every settings tab and in the full-screen editor with the effect, its look, each module's layer, an emitter and a
 * sound selected, and in the help.
 */
final class TooltipScenario {
    private TooltipScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        Set<String> missing = new LinkedHashSet<>();
        int[] seen = {0};
        context.setScreen(() -> new ShatterConfigScreen(null));
        context.waitTicks(3);
        for (ShatterConfigScreen.Tab tab : ShatterConfigScreen.Tab.values()) {
            context.runOnClient(mc -> EditorScenario.editor(mc).selectTab(tab));
            context.waitTicks(4);
            int before = seen[0];
            collect(context, missing, seen, tab.name());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: tab {} has {} controls", name, tab, seen[0] - before);
        }
        // The About section closes the General tab: its link buttons are there and explained, and each opens the
        // game's own "open this link?" screen with the page from fabric.mod.json; "No" comes back to the settings.
        context.runOnClient(mc -> EditorScenario.editor(mc).selectTab(ShatterConfigScreen.Tab.GENERAL));
        context.waitTicks(3);
        java.util.Map<String, String> pages = new java.util.LinkedHashMap<>();
        pages.put("modrinth", "https://modrinth.com/mod/shattergui");
        pages.put("curseforge", "https://www.curseforge.com/minecraft/mc-mods/shattergui");
        pages.put("github", "https://github.com/lilaktt/shatter-gui");
        pages.put("report_bug", "https://github.com/lilaktt/shatter-gui/issues");
        for (java.util.Map.Entry<String, String> page : pages.entrySet()) {
            String title = net.minecraft.network.chat.Component.translatable("ui.shatter_gui.about." + page.getKey()).getString();
            boolean opened = context.computeOnClient(mc -> {
                List<Widget> all = new ArrayList<>();
                walk(((UiScreen) mc.screen).rootWidget(), all);
                Button button = (Button) all.stream().filter(w -> w instanceof Button b && b.tooltipTitle() != null
                        && b.tooltipTitle().getString().equals(title)).findFirst().orElse(null);
                if (button == null || button.tooltipText() == null) {
                    return false;
                }
                button.press();
                return mc.screen instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen;
            });
            check.accept(opened, name + ": About must have the " + page.getKey() + " button, opening the link confirmation");
            if (opened) {
                String shown = context.computeOnClient(mc -> {
                    List<String> texts = new ArrayList<>();
                    for (var child : mc.screen.children()) {
                        texts.add(child.toString());
                    }
                    return mc.screen.getNarrationMessage().getString() + " " + texts;
                });
                check.accept(shown.contains(page.getValue()), name + ": the " + page.getKey() + " confirmation must show "
                        + page.getValue() + ", shows: " + shown);
                // Esc answers "No", as the No button does.
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
                context.waitTicks(2);
                check.accept(context.computeOnClient(mc -> mc.screen instanceof ShatterConfigScreen),
                        name + ": closing the link confirmation must come back to the settings");
            }
        }
        // fabric.mod.json: the homepage is Modrinth, Mod Menu gets Modrinth and CurseForge links.
        var meta = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("shatter_gui").orElseThrow().getMetadata();
        check.accept(pages.get("modrinth").equals(meta.getContact().get("homepage").orElse(null))
                && pages.get("github").equals(meta.getContact().get("sources").orElse(null))
                && pages.get("report_bug").equals(meta.getContact().get("issues").orElse(null))
                && pages.get("curseforge").equals(meta.getContact().get("curseforge").orElse(null)),
                name + ": fabric.mod.json contact must have the Modrinth homepage, GitHub sources and issues, CurseForge");
        var links = meta.getCustomValue("modmenu").getAsObject().get("links").getAsObject();
        check.accept(pages.get("modrinth").equals(links.get("modmenu.modrinth").getAsString())
                && pages.get("curseforge").equals(links.get("modmenu.curseforge").getAsString()),
                name + ": custom.modmenu.links must have Modrinth and CurseForge");
        context.runOnClient(mc -> {
            EditorScenario.editor(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM);
            EditorScenario.editor(mc).effectEditor().closeTour();
            EditorScenario.editor(mc).openEditorFullscreen();
        });
        context.waitTicks(4);
        context.runOnClient(mc -> editor(mc).closeTour());
        List<Selection> selections = new ArrayList<>(List.of(Selection.EFFECT, Selection.APPEARANCE, Selection.EDGE,
                new Selection(Selection.Kind.EMITTER, 0), new Selection(Selection.Kind.SOUND, 0)));
        for (Selection s : selections) {
            context.runOnClient(mc -> editor(mc).select(s));
            context.waitTicks(3);
            collect(context, missing, seen, "editor " + s);
        }
        // Each module in turn on layer 0.
        for (FxDef.Module module : FxDef.Module.values()) {
            context.runOnClient(mc -> {
                FxEditor e = editor(mc);
                e.doc.set("layers[0].module", net.shattergui.fx.FxSchema.moduleName(module));
                e.select(new Selection(Selection.Kind.LAYER, 0));
                e.refreshInspector();
            });
            context.waitTicks(3);
            collect(context, missing, seen, "layer " + module);
        }
        context.runOnClient(mc -> net.shattergui.editor.HelpDialog.open("layers"));
        context.waitTicks(4);
        shot.accept(name + "_help");
        collect(context, missing, seen, "help");
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} controls looked at, without a tooltip: {}", name, seen[0], missing);
        check.accept(missing.isEmpty(), name + ": controls without a tooltip: " + missing);
        context.runOnClient(mc -> {
            if (mc.screen instanceof UiScreen s) {
                for (UiScreen.Overlay o : List.copyOf(s.overlays())) {
                    s.closeOverlay(o);
                }
            }
        });
        context.runOnClient(mc -> mc.screen.onClose());
        context.waitTicks(2);
        context.runOnClient(mc -> mc.screen.onClose());
        context.waitTicks(3);
    }

    private static FxEditor editor(Minecraft mc) {
        return ((FxEditorScreen) mc.screen).editor();
    }

    private static void collect(ClientGameTestContext context, Set<String> missing, int[] seen, String where) {
        List<String> found = context.computeOnClient(mc -> {
            List<String> out = new ArrayList<>();
            UiScreen screen = (UiScreen) mc.screen;
            List<Widget> all = new ArrayList<>();
            walk(screen.rootWidget(), all);
            for (UiScreen.Overlay o : screen.overlays()) {
                walk(o, all);
            }
            for (Widget w : all) {
                if (!w.visible || !w.enabled || !control(w)) {
                    continue;
                }
                seen[0]++;
                if (UiScreen.tooltipOwner(w) == null) {
                    out.add(where + ": " + describe(w));
                }
            }
            return out;
        });
        missing.addAll(found);
    }

    /** Something the player clicks, drags, types into or tabs to. */
    private static boolean control(Widget w) {
        return w instanceof Button || w instanceof Toggle || w instanceof Slider || w instanceof Dropdown<?>
                || w instanceof TextField || w instanceof Swatch || w instanceof TabNav || w.focusable();
    }

    private static String describe(Widget w) {
        StringBuilder path = new StringBuilder(w.getClass().getName().replaceAll(".*\\.", ""));
        for (Widget p = w.parent(); p != null && path.length() < 160; p = p.parent()) {
            path.append(" < ").append(p.getClass().getName().replaceAll(".*\\.", ""));
        }
        return path.toString();
    }

    private static void walk(Widget w, List<Widget> out) {
        if (w == null || !w.visible) {
            return;
        }
        out.add(w);
        if (w instanceof net.shattergui.ui.ScrollView scroll) {
            walk(scroll.content(), out);
        }
        if (w instanceof Container c) {
            for (Widget child : c.children()) {
                walk(child, out);
            }
        }
    }
}
