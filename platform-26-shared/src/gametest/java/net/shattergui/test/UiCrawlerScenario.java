package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.editor.FxEditor;
import net.shattergui.editor.FxEditorScreen;
import net.shattergui.editor.Selection;
import net.shattergui.editor.Templates;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.ui.Button;
import net.shattergui.ui.Card;
import net.shattergui.ui.Column;
import net.shattergui.ui.Container;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Grid;
import net.shattergui.ui.Label;
import net.shattergui.ui.Row;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Slider;
import net.shattergui.ui.TabNav;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Toggle;
import net.shattergui.ui.UiFailures;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class UiCrawlerScenario {
    private static final int PAGE_BUDGET = 120;
    private static final List<String> POKED = List.of("ItemRow", "SectionRow", "Timeline", "TexturePanel", "GradientEditor",
            "ColorPicker", "SearchList", "CurveEditor", "Scrubber", "Box", "TargetControl", "Swatch", "Thumb", "LiveBox");
    private static int actions;
    private static int canary;

    private UiCrawlerScenario() {
    }

    static void runMain(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        int failuresBefore = UiFailures.count();
        int scaleBefore = context.computeOnClient(mc -> mc.options.guiScale().get());
        actions = 0;
        try {
            EditorScenario.setScale(context, 2);
            context.getInput().setCursorPos(3, 3);
            settingsTabs(context, shot, name);
        } finally {
            EditorScenario.setScale(context, scaleBefore);
            context.runOnClient(mc -> {
                if (Mc26.screen() != null) {
                    Mc26.setScreen(null);
                }
            });
        }
        int failures = UiFailures.count() - failuresBefore;
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} actions, UI failures {} {}", name, actions, failures, UiFailures.kinds());
        check.accept(failures == 0, name + ": no widget may fail; failures: " + UiFailures.kinds());
    }

    private static void settingsTabs(ClientGameTestContext context, Consumer<String> shot, String name) {
        context.setScreen(() -> new ShatterConfigScreen(null));
        context.waitTicks(3);
        for (ShatterConfigScreen.Tab tab : new ShatterConfigScreen.Tab[]{ShatterConfigScreen.Tab.GENERAL,
                ShatterConfigScreen.Tab.EFFECTS, ShatterConfigScreen.Tab.SCREENS}) {
            context.runOnClient(mc -> settings(mc).selectTab(tab));
            context.waitTicks(3);
            crawl(context, name + "_" + tab.name().toLowerCase(Locale.ROOT), UiScreen::rootWidget, ShatterConfigScreen.class);
            shot.accept(name + "_" + tab.name().toLowerCase(Locale.ROOT));
        }
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        int failuresBefore = UiFailures.count();
        int scaleBefore = context.computeOnClient(mc -> mc.options.guiScale().get());
        actions = 0;
        canary = 0;
        Path[] made = new Path[0];
        try {
            made = textures();
            EditorScenario.setScale(context, 2);
            context.getInput().setCursorPos(3, 3);

            settingsTabs(context, shot, name);
            context.runOnClient(mc -> {
                settings(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM);
                settings(mc).setClassicCustom(true);
            });
            context.waitTicks(3);
            crawl(context, name + "_classic", UiScreen::rootWidget, ShatterConfigScreen.class);
            context.runOnClient(mc -> settings(mc).setClassicCustom(false));
            context.waitTicks(3);

            context.runOnClient(mc -> {
                settings(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM);
                settings(mc).setClassicCustom(false);
                settings(mc).effectEditor().closeTour();
            });
            context.waitTicks(3);
            crawlEditor(context, shot, name + "_embedded", true, ShatterConfigScreen.class);
            context.runOnClient(mc -> settings(mc).openEditorFullscreen());
            context.waitFor(mc -> Mc26.screen() instanceof FxEditorScreen, 100);
            context.waitTicks(3);
            crawlEditor(context, shot, name + "_full", false, FxEditorScreen.class);
            dialogs(context, shot, name);
            keyboard(context);
            undoStorm(context);
            files(context, check, name);
            brokenMouse(context);
            canary = canary(context, check, shot, name);

            context.runOnClient(mc -> ((FxEditorScreen) Mc26.screen()).back());
            context.waitTicks(2);
            context.runOnClient(mc -> Mc26.screen().onClose());
            context.waitTicks(2);
            scalesAndWindows(context, shot, name);
        } catch (IOException e) {
            check.accept(false, name + ": " + e);
        } finally {
            context.getInput().resizeWindow(1920, 1080);
            EditorScenario.setScale(context, scaleBefore);
            context.runOnClient(mc -> {
                if (Mc26.screen() != null) {
                    Mc26.setScreen(null);
                }
            });
            for (Path p : made) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            }
            cleanEffects();
            context.runOnClient(mc -> FxEffects.reloadAll());
        }
        int failures = UiFailures.count() - failuresBefore - canary;
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} actions, UI failures {} {}", name, actions, failures, UiFailures.kinds());
        check.accept(failures == 0, name + ": no widget may fail; failures: " + UiFailures.kinds());
    }

    private static void crawlEditor(ClientGameTestContext context, Consumer<String> shot, String label, boolean light,
                                    Class<? extends UiScreen> screen) {
        Function<UiScreen, Widget> scope = s -> editor(s);
        page(context, label + "_effect", scope, screen, e -> {
            e.newEffect("glass");
            e.select(Selection.EFFECT);
        });
        shot.accept(label + "_effect");
        page(context, label + "_appearance", scope, screen, e -> {
            e.addOverlay(null);
            e.useTexture("crawl_mask", "mask");
            e.select(Selection.APPEARANCE);
        });
        page(context, label + "_edge", scope, screen, e -> {
            if (!e.doc.has("modifiers.edge")) {
                e.toggleEdge();
            }
            e.select(Selection.EDGE);
        });
        for (FxDef.Module module : FxDef.Module.values()) {
            if (light && module.ordinal() > 1) {
                break;
            }
            page(context, label + "_layer_" + module.name().toLowerCase(Locale.ROOT), scope, screen, e -> {
                int index = e.addLayer(module);
                e.select(new Selection(Selection.Kind.LAYER, index));
                e.showParamCurve(index, "value");
                e.showParamCurve(index, "speed");
                e.showParamCurve(index, "strength");
            });
            context.runOnClient(mc -> {
                FxEditor e = editor(Mc26.screen());
                int last = e.doc.size("layers") - 1;
                if (last >= 0) {
                    e.removeItem(new Selection(Selection.Kind.LAYER, last));
                }
            });
        }
        page(context, label + "_emitter_vanilla", scope, screen, e -> e.select(new Selection(Selection.Kind.EMITTER,
                e.addEmitter(false))));
        page(context, label + "_emitter_sprite", scope, screen, e -> {
            e.chooseTexture("crawl_drop");
            e.select(new Selection(Selection.Kind.EMITTER, e.addEmitter(true)));
        });
        shot.accept(label + "_emitter_sprite");
        page(context, label + "_sound", scope, screen, e -> e.select(new Selection(Selection.Kind.SOUND, e.addSound())));
        page(context, label + "_textures", scope, screen, e -> {
            e.showLeftTab(1);
            e.chooseTexture("crawl_mask");
        });
        shot.accept(label + "_textures");
        context.runOnClient(mc -> editor(Mc26.screen()).showLeftTab(0));
        for (String template : Templates.IDS) {
            context.runOnClient(mc -> editor(Mc26.screen()).newEffect(template));
            context.waitTicks(4);
        }
    }

    private static void page(ClientGameTestContext context, String label, Function<UiScreen, Widget> scope,
                             Class<? extends UiScreen> screen, Consumer<FxEditor> setup) {
        context.runOnClient(mc -> setup.accept(editor(Mc26.screen())));
        context.waitTicks(3);
        crawl(context, label, scope, screen);
    }

    private static void crawl(ClientGameTestContext context, String label, Function<UiScreen, Widget> scope,
                              Class<? extends UiScreen> screen) {
        int before = UiFailures.count();
        int done = 0;
        for (int i = 0; i < PAGE_BUDGET; i++) {
            int index = i;
            Widget target = context.computeOnClient(mc -> {
                if (!screen.isInstance(Mc26.screen())) {
                    return null;
                }
                UiScreen s = (UiScreen) Mc26.screen();
                closeOverlays(s);
                List<Widget> all = new ArrayList<>();
                collect(scope.apply(s), all);
                return index < all.size() ? all.get(index) : null;
            });
            if (target == null) {
                break;
            }
            exercise(context, target);
            done++;
            boolean restored = context.computeOnClient(mc -> {
                if (screen == ShatterConfigScreen.class && Mc26.screen() instanceof ShatterConfigScreen s && label.contains("_embedded")) {
                    if (s.classicCustom() || s.tab() != ShatterConfigScreen.Tab.CUSTOM) {
                        s.selectTab(ShatterConfigScreen.Tab.CUSTOM);
                        s.setClassicCustom(false);
                        return true;
                    }
                }
                return false;
            });
            if (restored) {
                context.waitTicks(2);
            }
            if (!context.computeOnClient(mc -> screen.isInstance(Mc26.screen()))) {
                ShatterGuiClient.LOGGER.info("[ShatterTest] {}: the screen changed after {} ({}), page ends", label,
                        target.getClass().getSimpleName(), i);
                break;
            }
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} widgets exercised, {} new UI failures", label, done,
                UiFailures.count() - before);
    }

    private static void collect(Widget widget, List<Widget> out) {
        if (widget == null || !widget.visible || widget.w <= 0 || widget.h <= 0) {
            return;
        }
        out.add(widget);
        if (widget instanceof ScrollView scroll) {
            collect(scroll.content(), out);
        } else if (widget instanceof Container container) {
            for (Widget child : List.copyOf(container.children())) {
                collect(child, out);
            }
        }
    }

    private static void exercise(ClientGameTestContext context, Widget w) {
        actions++;
        switch (w) {
            case Dropdown<?> d -> {
                context.runOnClient(mc -> d.openList());
                context.waitTick();
                int n = context.computeOnClient(mc -> d.optionCount());
                for (int k = 0; k < Math.min(n, 16); k++) {
                    int option = k;
                    context.runOnClient(mc -> {
                        closeOverlays((UiScreen) Mc26.screen());
                        d.choose(option);
                    });
                    context.waitTick();
                }
            }
            case Slider s -> {
                context.runOnClient(mc -> s.setValue(s.min()));
                context.waitTick();
                context.runOnClient(mc -> s.setValue(s.max()));
                context.waitTick();
                for (String typed : new String[]{"1e99", "abc", "-0,5", ""}) {
                    context.runOnClient(mc -> {
                        s.startEdit();
                        s.field().typeText(typed);
                        ((UiScreen) Mc26.screen()).keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    });
                    context.waitTick();
                }
            }
            case Toggle t -> {
                context.runOnClient(mc -> t.flip());
                context.waitTick();
                context.runOnClient(mc -> t.flip());
                context.waitTick();
            }
            case TextField f -> {
                for (String typed : new String[]{"Тест <>\"\\ ✓ 🙂", "", "x".repeat(300), "a_b"}) {
                    context.runOnClient(mc -> {
                        ((UiScreen) Mc26.screen()).setFocus(f);
                        f.typeText(typed);
                        ((UiScreen) Mc26.screen()).charTyped(new CharacterEvent('z'));
                    });
                    context.waitTick();
                }
                context.runOnClient(mc -> ((UiScreen) Mc26.screen()).setFocus(null));
            }
            case Button b -> {
                if (!b.leavesScreen() && b.enabled) {
                    context.runOnClient(mc -> b.press());
                    context.waitTick();
                }
            }
            case TabNav nav -> {
            }
            default -> {
                String type = w.getClass().getSimpleName();
                boolean clickable = w instanceof Card card && card.focusable();
                if (clickable || POKED.contains(type) || !(w instanceof Container) && !(w instanceof Label)) {
                    poke(context, w);
                }
                if (w instanceof Column || w instanceof Row || w instanceof Grid || w instanceof ScrollView) {
                    context.runOnClient(mc -> ((UiScreen) Mc26.screen()).mouseScrolled(w.x + w.w / 2.0, w.y + w.h / 2.0, 0, -1));
                }
            }
        }
    }

    private static void poke(ClientGameTestContext context, Widget w) {
        double cx = w.x + w.w / 2.0;
        double cy = w.y + w.h / 2.0;
        boolean safe = context.computeOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            Widget hit = s.rootWidget().hit(cx, cy);
            if (s.hasOverlay() || hit == null) {
                return false;
            }
            boolean inside = false;
            for (Widget p = hit; p != null; p = p.parent()) {
                if (p == w) {
                    inside = true;
                }
                if (p instanceof Button b && b.leavesScreen()) {
                    return false;
                }
            }
            return inside;
        });
        if (!safe) {
            return;
        }
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            s.mouseDragged(new MouseButtonEvent(cx + 12, cy + 4, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), 12, 4);
            s.mouseReleased(new MouseButtonEvent(cx + 12, cy + 4, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
        });
        context.waitTick();
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            closeOverlays(s);
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, 0)), false);
            s.mouseReleased(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, 0)));
            closeOverlays(s);
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            s.mouseReleased(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), true);
            s.mouseReleased(new MouseButtonEvent(cx, cy, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
            s.mouseScrolled(cx, cy, 0, 1);
            s.mouseScrolled(cx, cy, 0, -1);
            s.setFocus(null);
        });
        context.waitTick();
    }

    private static void closeOverlays(UiScreen screen) {
        for (UiScreen.Overlay overlay : List.copyOf(screen.overlays())) {
            screen.closeOverlay(overlay);
        }
    }

    private static void dialogs(ClientGameTestContext context, Consumer<String> shot, String name) {
        String[] kinds = {"catalog", "gallery", "import", "menu", "tour"};
        for (String kind : kinds) {
            context.runOnClient(mc -> {
                FxEditor e = editor(Mc26.screen());
                switch (kind) {
                    case "catalog" -> e.openCatalog();
                    case "gallery" -> e.openGallery();
                    case "import" -> {
                        e.exportPackage();
                        e.openImport();
                    }
                    case "menu" -> e.openEffectsMenu();
                    default -> e.startTour();
                }
            });
            context.waitTicks(6);
            shot.accept(name + "_dialog_" + kind);
            for (int[] f : new int[][]{{50, 50}, {30, 30}, {70, 80}}) {
                context.runOnClient(mc -> {
                    UiScreen s = (UiScreen) Mc26.screen();
                    if (!s.hasOverlay()) {
                        return;
                    }
                    UiScreen.Overlay o = s.overlays().getLast();
                    double x = o.x + o.w * f[0] / 100.0;
                    double y = o.y + o.h * f[1] / 100.0;
                    s.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    s.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
                    s.mouseScrolled(x, y, 0, -2);
                });
                context.waitTicks(3);
                context.runOnClient(mc -> {
                    if (Mc26.screen() instanceof UiScreen s && s.hasOverlay()
                            && s.overlays().getLast() instanceof net.shattergui.ui.ConfirmDialog) {
                        s.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                    }
                });
            }
            context.runOnClient(mc -> {
                if (Mc26.screen() instanceof UiScreen s) {
                    for (int i = 0; i < 6 && s.hasOverlay(); i++) {
                        s.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                    }
                }
            });
            context.waitTicks(2);
            if (!context.computeOnClient(mc -> Mc26.screen() instanceof FxEditorScreen)) {
                context.runOnClient(mc -> ShatterGuiClient.LOGGER.info("[ShatterTest] dialog {} left the screen: {}", kind,
                        Mc26.screen() == null ? "none" : Mc26.screen().getClass().getSimpleName()));
                break;
            }
        }
    }

    private static void keyboard(ClientGameTestContext context) {
        int[][] keys = {{InputConstants.KEY_TAB, 0}, {InputConstants.KEY_TAB, 0}, {InputConstants.KEY_DOWN, 0},
                {InputConstants.KEY_UP, 0}, {InputConstants.KEY_LEFT, 0}, {InputConstants.KEY_RIGHT, 0},
                {InputConstants.KEY_SPACE, 0}, {InputConstants.KEY_Z, InputConstants.MOD_CONTROL}, {InputConstants.KEY_Y, InputConstants.MOD_CONTROL},
                {InputConstants.KEY_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT}, {InputConstants.KEY_F2, 0}, {InputConstants.KEY_DELETE, 0},
                {InputConstants.KEY_HOME, 0}, {InputConstants.KEY_END, 0}, {InputConstants.KEY_BACKSPACE, 0}};
        for (int round = 0; round < 12; round++) {
            for (int[] k : keys) {
                context.runOnClient(mc -> {
                    if (Mc26.screen() instanceof FxEditorScreen s) {
                        s.keyPressed(new KeyEvent(k[0], 0, k[1]));
                        s.charTyped(new CharacterEvent('q'));
                        if (s.hasOverlay()) {
                            s.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                        }
                    }
                });
            }
            context.waitTick();
        }
    }

    private static void undoStorm(ClientGameTestContext context) {
        for (int i = 0; i < 80; i++) {
            int step = i;
            context.runOnClient(mc -> {
                FxEditor e = editor(Mc26.screen());
                if (step < 40) {
                    e.undo();
                } else {
                    e.redo();
                }
            });
            if (i % 4 == 0) {
                context.waitTick();
            }
        }
        context.waitTicks(2);
    }

    private static void files(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        context.runOnClient(mc -> {
            FxEditor e = editor(Mc26.screen());
            e.newEffect("vortex");
            e.doc.set("id", "crawl_fx");
            e.save();
        });
        context.waitTicks(3);
        Path exported = context.computeOnClient(mc -> editor(Mc26.screen()).exportPackage());
        context.runOnClient(mc -> editor(Mc26.screen()).deleteEffect("crawl_fx"));
        context.waitTicks(2);
        boolean imported = context.computeOnClient(mc -> exported != null && editor(Mc26.screen()).importPackage(exported).ok());
        check.accept(imported, name + ": export, delete and import back");
        context.runOnClient(mc -> {
            mc.keyboardHandler.setClipboard("not a code at all \u0000￿");
            editor(Mc26.screen()).importClipboard();
            mc.keyboardHandler.setClipboard(FxEffects.codeOf(Templates.json("rain")));
            editor(Mc26.screen()).importClipboard();
        });
        context.waitTicks(4);
    }

    private static int canary(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        int before = UiFailures.count();
        Widget[] made = new Widget[2];
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            Widget drawFails = new Widget() {
                @Override
                protected void draw(net.shattergui.ui.Ui ui) {
                    throw new IllegalStateException("canary: drawing fails on purpose");
                }
            };
            drawFails.setBounds(40, 40, 120, 20);
            Widget clickFails = new Widget() {
                @Override
                protected void draw(net.shattergui.ui.Ui ui) {
                    net.shattergui.ui.Draw.rect(ui, x, y, w, h, 0xFF336633);
                }

                @Override
                public boolean mouseDown(double mx, double my, int button) {
                    throw new IllegalStateException("canary: clicking fails on purpose");
                }
            };
            clickFails.setBounds(40, 70, 120, 20);
            s.rootWidget().add(drawFails);
            s.rootWidget().add(clickFails);
            made[0] = drawFails;
            made[1] = clickFails;
        });
        context.waitTicks(10);
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            s.mouseClicked(new MouseButtonEvent(60, 80, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            s.mouseReleased(new MouseButtonEvent(60, 80, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
            s.mouseClicked(new MouseButtonEvent(60, 80, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            s.mouseReleased(new MouseButtonEvent(60, 80, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
        });
        context.waitTicks(10);
        shot.accept(name + "_canary_error_boxes");
        int caused = UiFailures.count() - before;
        boolean boxes = context.computeOnClient(mc -> made[0].broken() && made[1].broken());
        context.runOnClient(mc -> editor(Mc26.screen()).doc.set("duration", 33));
        context.waitTicks(3);
        boolean alive = context.computeOnClient(mc -> Mc26.screen() instanceof FxEditorScreen
                && editor(Mc26.screen()).doc.num("duration", 0) == 33 && editor(Mc26.screen()).shown());
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            s.rootWidget().children().remove(made[0]);
            s.rootWidget().children().remove(made[1]);
        });
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: canary caused {} failures (kinds now {}), error boxes {}, screen alive {}",
                name, caused, UiFailures.kinds().size(), boxes, alive);
        check.accept(caused == 2, name + ": a failing widget is reported once per kind, not per frame (" + caused + ")");
        check.accept(boxes && alive, name + ": failing widgets become error boxes and the rest keeps working");
        return caused;
    }

    private static void brokenMouse(ClientGameTestContext context) {
        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e300};
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) Mc26.screen();
            for (double v : bad) {
                s.mouseClicked(new MouseButtonEvent(v, v, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                s.mouseClicked(new MouseButtonEvent(10, v, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), true);
                s.mouseDragged(new MouseButtonEvent(v, 10, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), v, v);
                s.mouseReleased(new MouseButtonEvent(v, v, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
                s.mouseScrolled(v, v, v, v);
                s.mouseScrolled(10, 10, 0, v);
            }
            FxEditor e = editor(s);
            double x = e.x + e.w / 2.0;
            double y = e.y + e.h - 30;
            s.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            s.mouseDragged(new MouseButtonEvent(Double.POSITIVE_INFINITY, y, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), 1, 0);
            s.mouseDragged(new MouseButtonEvent(x + 5, y, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), 1, 0);
            s.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
        });
        context.waitTicks(3);
    }

    private static void scalesAndWindows(ClientGameTestContext context, Consumer<String> shot, String name) {
        int[][] windows = {{1920, 1080}, {1280, 720}, {854, 480}, {700, 420}};
        for (int scale = 1; scale <= 4; scale++) {
            EditorScenario.setScale(context, scale);
            for (int[] size : windows) {
                context.getInput().resizeWindow(size[0], size[1]);
                context.waitTicks(2);
                context.setScreen(() -> new ShatterConfigScreen(null));
                context.waitTicks(2);
                for (ShatterConfigScreen.Tab tab : ShatterConfigScreen.Tab.values()) {
                    context.runOnClient(mc -> settings(mc).selectTab(tab));
                    context.waitTicks(2);
                }
                context.runOnClient(mc -> {
                    settings(mc).effectEditor().closeTour();
                    settings(mc).openEditorFullscreen();
                });
                context.waitTicks(3);
                if (context.computeOnClient(mc -> Mc26.screen() instanceof FxEditorScreen)) {
                    for (Selection part : new Selection[]{Selection.EFFECT, Selection.APPEARANCE,
                            new Selection(Selection.Kind.LAYER, 0), new Selection(Selection.Kind.EMITTER, 0),
                            new Selection(Selection.Kind.SOUND, 0)}) {
                        context.runOnClient(mc -> editor(Mc26.screen()).select(part));
                        context.waitTicks(2);
                        openEveryList(context);
                    }
                    if (size[0] == 1920) {
                        shot.accept(name + "_s" + scale + "_" + size[0] + "x" + size[1]);
                    }
                    context.runOnClient(mc -> ((FxEditorScreen) Mc26.screen()).back());
                    context.waitTicks(2);
                }
                context.runOnClient(mc -> {
                    if (Mc26.screen() != null) {
                        Mc26.screen().onClose();
                    }
                });
                context.waitTicks(2);
            }
        }
    }

    private static void openEveryList(ClientGameTestContext context) {
        int lists = context.computeOnClient(mc -> dropdowns((UiScreen) Mc26.screen()).size());
        for (int i = 0; i < lists; i++) {
            int index = i;
            context.runOnClient(mc -> {
                UiScreen s = (UiScreen) Mc26.screen();
                closeOverlays(s);
                List<Dropdown<?>> all = dropdowns(s);
                if (index < all.size()) {
                    all.get(index).openList();
                }
            });
            context.waitTick();
        }
        context.runOnClient(mc -> closeOverlays((UiScreen) Mc26.screen()));
    }

    private static List<Dropdown<?>> dropdowns(UiScreen screen) {
        List<Widget> all = new ArrayList<>();
        collect(screen.rootWidget(), all);
        List<Dropdown<?>> out = new ArrayList<>();
        for (Widget w : all) {
            if (w instanceof Dropdown<?> d) {
                out.add(d);
            }
        }
        return out;
    }

    private static ShatterConfigScreen settings(Minecraft mc) {
        return EditorScenario.editor(mc);
    }

    private static FxEditor editor(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof FxEditorScreen full) {
            return full.editor();
        }
        return ((ShatterConfigScreen) screen).effectEditor();
    }

    private static FxEditor editor(UiScreen screen) {
        return editor((net.minecraft.client.gui.screens.Screen) screen);
    }

    private static Path[] textures() throws IOException {
        Files.createDirectories(FxEffects.TEXTURES);
        Path mask = FxEffects.TEXTURES.resolve("crawl_mask.png");
        Path drop = FxEffects.TEXTURES.resolve("crawl_drop.png");
        Path badName = FxEffects.TEXTURES.resolve("Crawl Bad Name.png");
        Path notPng = FxEffects.TEXTURES.resolve("crawl_not_png.png");
        Path large = FxEffects.TEXTURES.resolve("crawl_large.png");
        copy("/assets/shatter_gui_test/shatter_gui/textures/burn_mask.png", mask);
        copy("/assets/shatter_gui_test/shatter_gui/textures/raindrop.png", drop);
        copy("/assets/shatter_gui_test/shatter_gui/textures/raindrop.png", badName);
        Files.writeString(notPng, "hello, I am not a picture", StandardCharsets.UTF_8);
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R',
                0, 0, 0x0F, (byte) 0xA0, 0, 0, 0x0F, (byte) 0xA0, 8, 6, 0, 0, 0};
        Files.write(large, header);
        return new Path[]{mask, drop, badName, notPng, large};
    }

    private static void copy(String resource, Path to) throws IOException {
        try (InputStream in = UiCrawlerScenario.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing test resource " + resource);
            }
            Files.write(to, in.readAllBytes());
        }
    }

    private static void cleanEffects() {
        for (String id : new String[]{"crawl_fx", "crawl_fx_2", "particle_rain", "particle_rain_2"}) {
            try {
                Files.deleteIfExists(FxEffects.EFFECTS.resolve(id + ".json"));
            } catch (IOException ignored) {
            }
        }
        try (var files = Files.list(FxEffects.EFFECTS)) {
            for (Path p : files.toList()) {
                if (p.getFileName().toString().startsWith("my_")) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(FxEffects.EXPORTS.resolve("crawl_fx.shatterfx"));
        } catch (IOException ignored) {
        }
    }
}
