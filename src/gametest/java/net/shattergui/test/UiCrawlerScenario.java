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

/**
 * The UI crawler: goes through the settings and the effect editor and does everything a player could — every tab,
 * every drop-down opened and every option chosen, sliders to their minimum and maximum and nonsense typed into their
 * exact-value boxes, toggles flipped, text fields fed odd text, buttons pressed (except those that leave the screen or
 * open a folder), and a click, drag, right click, double click and scroll on everything else, through the screen's real
 * input path. Then: every module added, changed and removed, every sample interface, export/import, undo/redo
 * storms, the keyboard, broken mouse positions (NaN, infinity), GUI scales 1–4 and several window sizes. Passes when
 * no widget failed ({@link UiFailures}: no error boxes, nothing in the log).
 */
final class UiCrawlerScenario {
    private static final int PAGE_BUDGET = 120;
    private static final List<String> POKED = List.of("ItemRow", "SectionRow", "Timeline", "TexturePanel", "GradientEditor",
            "ColorPicker", "SearchList", "CurveEditor", "Scrubber", "Box", "TargetControl", "Swatch", "Thumb", "LiveBox");
    private static int actions;
    /** Failures caused on purpose by the canary (not counted against the run). */
    private static int canary;

    private UiCrawlerScenario() {
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

            // --- the settings ---
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            for (ShatterConfigScreen.Tab tab : new ShatterConfigScreen.Tab[]{ShatterConfigScreen.Tab.GENERAL,
                    ShatterConfigScreen.Tab.EFFECTS, ShatterConfigScreen.Tab.SCREENS}) {
                context.runOnClient(mc -> settings(mc).selectTab(tab));
                context.waitTicks(3);
                crawl(context, name + "_" + tab.name().toLowerCase(Locale.ROOT), UiScreen::rootWidget, ShatterConfigScreen.class);
                shot.accept(name + "_" + tab.name().toLowerCase(Locale.ROOT));
            }
            context.runOnClient(mc -> {
                settings(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM);
                settings(mc).setClassicCustom(true);
            });
            context.waitTicks(3);
            crawl(context, name + "_classic", UiScreen::rootWidget, ShatterConfigScreen.class);
            context.runOnClient(mc -> settings(mc).setClassicCustom(false));
            context.waitTicks(3);

            // --- the editor inside the settings, then on the whole screen ---
            // The classic page's effect cards open the Effects tab: back to Custom for the editor.
            context.runOnClient(mc -> {
                settings(mc).selectTab(ShatterConfigScreen.Tab.CUSTOM);
                settings(mc).setClassicCustom(false);
                settings(mc).effectEditor().closeTour();
            });
            context.waitTicks(3);
            crawlEditor(context, shot, name + "_embedded", true, ShatterConfigScreen.class);
            context.runOnClient(mc -> settings(mc).openEditorFullscreen());
            context.waitFor(mc -> mc.screen instanceof FxEditorScreen, 100);
            context.waitTicks(3);
            crawlEditor(context, shot, name + "_full", false, FxEditorScreen.class);
            dialogs(context, shot, name);
            keyboard(context);
            undoStorm(context);
            files(context, check, name);
            brokenMouse(context);
            canary = canary(context, check, shot, name);

            // --- scales and window sizes ---
            context.runOnClient(mc -> ((FxEditorScreen) mc.screen).back());
            context.waitTicks(2);
            context.runOnClient(mc -> mc.screen.onClose());
            context.waitTicks(2);
            scalesAndWindows(context, shot, name);
        } catch (IOException e) {
            check.accept(false, name + ": " + e);
        } finally {
            context.getInput().resizeWindow(1920, 1080);
            EditorScenario.setScale(context, scaleBefore);
            context.runOnClient(mc -> {
                if (mc.screen != null) {
                    mc.setScreen(null);
                }
            });
            for (Path p : made) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Test run folder only.
                }
            }
            cleanEffects();
            context.runOnClient(mc -> FxEffects.reloadAll());
        }
        int failures = UiFailures.count() - failuresBefore - canary;
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} actions, UI failures {} {}", name, actions, failures, UiFailures.kinds());
        check.accept(failures == 0, name + ": no widget may fail; failures: " + UiFailures.kinds());
    }

    // --- pages of the editor ---

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
                FxEditor e = editor(mc.screen);
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
        context.runOnClient(mc -> editor(mc.screen).showLeftTab(0));
        // Every template once on screen.
        for (String template : Templates.IDS) {
            context.runOnClient(mc -> editor(mc.screen).newEffect(template));
            context.waitTicks(4);
        }
    }

    /** Sets a page up, then crawls it. */
    private static void page(ClientGameTestContext context, String label, Function<UiScreen, Widget> scope,
                             Class<? extends UiScreen> screen, Consumer<FxEditor> setup) {
        context.runOnClient(mc -> setup.accept(editor(mc.screen)));
        context.waitTicks(3);
        crawl(context, label, scope, screen);
    }

    // --- the crawl itself ---

    /** Exercises every widget of the scope, one action per tick (the screen redraws in between). */
    private static void crawl(ClientGameTestContext context, String label, Function<UiScreen, Widget> scope,
                              Class<? extends UiScreen> screen) {
        int before = UiFailures.count();
        int done = 0;
        for (int i = 0; i < PAGE_BUDGET; i++) {
            int index = i;
            Widget target = context.computeOnClient(mc -> {
                if (!screen.isInstance(mc.screen)) {
                    return null;
                }
                UiScreen s = (UiScreen) mc.screen;
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
            // Inside the settings the editor's "Format 1" switches to the classic page: back to the editor.
            boolean restored = context.computeOnClient(mc -> {
                if (screen == ShatterConfigScreen.class && mc.screen instanceof ShatterConfigScreen s && label.contains("_embedded")) {
                    if (s.classicCustom() || s.tab() != ShatterConfigScreen.Tab.CUSTOM) {
                        s.selectTab(ShatterConfigScreen.Tab.CUSTOM);
                        s.setClassicCustom(false);
                        return true;
                    }
                }
                return false;
            });
            if (restored) {
                // The editor is placed (and shown) by the next frame's layout.
                context.waitTicks(2);
            }
            if (!context.computeOnClient(mc -> screen.isInstance(mc.screen))) {
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
                        closeOverlays((UiScreen) mc.screen);
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
                        ((UiScreen) mc.screen).keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
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
                        ((UiScreen) mc.screen).setFocus(f);
                        f.typeText(typed);
                        ((UiScreen) mc.screen).charTyped(new CharacterEvent('z'));
                    });
                    context.waitTick();
                }
                context.runOnClient(mc -> ((UiScreen) mc.screen).setFocus(null));
            }
            case Button b -> {
                if (!b.leavesScreen() && b.enabled) {
                    context.runOnClient(mc -> b.press());
                    context.waitTick();
                }
            }
            case TabNav nav -> {
                // Navigation tabs change the page: the pages are visited on purpose instead.
            }
            default -> {
                String type = w.getClass().getSimpleName();
                boolean clickable = w instanceof Card card && card.focusable();
                if (clickable || POKED.contains(type) || !(w instanceof Container) && !(w instanceof Label)) {
                    poke(context, w);
                }
                if (w instanceof Column || w instanceof Row || w instanceof Grid || w instanceof ScrollView) {
                    context.runOnClient(mc -> ((UiScreen) mc.screen).mouseScrolled(w.x + w.w / 2.0, w.y + w.h / 2.0, 0, -1));
                }
            }
        }
    }

    /** A click, a drag, a right click, a double click and a scroll in the middle of the widget, via the screen. */
    private static void poke(ClientGameTestContext context, Widget w) {
        double cx = w.x + w.w / 2.0;
        double cy = w.y + w.h / 2.0;
        boolean safe = context.computeOnClient(mc -> {
            UiScreen s = (UiScreen) mc.screen;
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
            UiScreen s = (UiScreen) mc.screen;
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), false);
            s.mouseDragged(new MouseButtonEvent(cx + 12, cy + 4, new MouseButtonInfo(0, 0)), 12, 4);
            s.mouseReleased(new MouseButtonEvent(cx + 12, cy + 4, new MouseButtonInfo(0, 0)));
        });
        context.waitTick();
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) mc.screen;
            closeOverlays(s);
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(1, 0)), false);
            s.mouseReleased(new MouseButtonEvent(cx, cy, new MouseButtonInfo(1, 0)));
            closeOverlays(s);
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), false);
            s.mouseReleased(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)));
            s.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), true);
            s.mouseReleased(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)));
            s.mouseScrolled(cx, cy, 0, 1);
            s.mouseScrolled(cx, cy, 0, -1);
            // A rename may have started: focus elsewhere ends it (Esc would close the screen).
            s.setFocus(null);
        });
        context.waitTick();
    }

    /** Closes lists and dialogs (a modal question is answered with "no"). */
    private static void closeOverlays(UiScreen screen) {
        for (UiScreen.Overlay overlay : List.copyOf(screen.overlays())) {
            screen.closeOverlay(overlay);
        }
    }

    // --- the rest ---

    private static void dialogs(ClientGameTestContext context, Consumer<String> shot, String name) {
        String[] kinds = {"catalog", "gallery", "import", "menu", "tour"};
        for (String kind : kinds) {
            context.runOnClient(mc -> {
                FxEditor e = editor(mc.screen);
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
            // Click into the dialog: its first element, the middle, a corner.
            for (int[] f : new int[][]{{50, 50}, {30, 30}, {70, 80}}) {
                context.runOnClient(mc -> {
                    UiScreen s = (UiScreen) mc.screen;
                    if (!s.hasOverlay()) {
                        return;
                    }
                    UiScreen.Overlay o = s.overlays().getLast();
                    double x = o.x + o.w * f[0] / 100.0;
                    double y = o.y + o.h * f[1] / 100.0;
                    s.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
                    s.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)));
                    s.mouseScrolled(x, y, 0, -2);
                });
                context.waitTicks(3);
                // An import may ask first: answer no.
                context.runOnClient(mc -> {
                    if (mc.screen instanceof UiScreen s && s.hasOverlay()
                            && s.overlays().getLast() instanceof net.shattergui.ui.ConfirmDialog) {
                        s.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                    }
                });
            }
            context.runOnClient(mc -> {
                if (mc.screen instanceof UiScreen s) {
                    for (int i = 0; i < 6 && s.hasOverlay(); i++) {
                        s.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                    }
                }
            });
            context.waitTicks(2);
            if (!context.computeOnClient(mc -> mc.screen instanceof FxEditorScreen)) {
                // A dialog led away (e.g. an import asked nothing and opened): come back.
                context.runOnClient(mc -> ShatterGuiClient.LOGGER.info("[ShatterTest] dialog {} left the screen: {}", kind,
                        mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
                break;
            }
        }
    }

    private static void keyboard(ClientGameTestContext context) {
        int[][] keys = {{InputConstants.KEY_TAB, 0}, {InputConstants.KEY_TAB, 0}, {InputConstants.KEY_DOWN, 0},
                {InputConstants.KEY_UP, 0}, {InputConstants.KEY_LEFT, 0}, {InputConstants.KEY_RIGHT, 0},
                {InputConstants.KEY_SPACE, 0}, {InputConstants.KEY_Z, 2}, {InputConstants.KEY_Y, 2},
                {InputConstants.KEY_Z, 3}, {InputConstants.KEY_F2, 0}, {InputConstants.KEY_DELETE, 0},
                {InputConstants.KEY_HOME, 0}, {InputConstants.KEY_END, 0}, {InputConstants.KEY_BACKSPACE, 0}};
        for (int round = 0; round < 12; round++) {
            for (int[] k : keys) {
                context.runOnClient(mc -> {
                    if (mc.screen instanceof FxEditorScreen s) {
                        s.keyPressed(new KeyEvent(k[0], 0, k[1]));
                        s.charTyped(new CharacterEvent('q'));
                        // A rename or a list: back out.
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
                FxEditor e = editor(mc.screen);
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
            FxEditor e = editor(mc.screen);
            e.newEffect("vortex");
            e.doc.set("id", "crawl_fx");
            e.save();
        });
        context.waitTicks(3);
        Path exported = context.computeOnClient(mc -> editor(mc.screen).exportPackage());
        context.runOnClient(mc -> editor(mc.screen).deleteEffect("crawl_fx"));
        context.waitTicks(2);
        boolean imported = context.computeOnClient(mc -> exported != null && editor(mc.screen).importPackage(exported).ok());
        check.accept(imported, name + ": export, delete and import back");
        // Garbage in the clipboard, then a real code.
        context.runOnClient(mc -> {
            mc.keyboardHandler.setClipboard("not a code at all \u0000￿");
            editor(mc.screen).importClipboard();
            mc.keyboardHandler.setClipboard(FxEffects.codeOf(Templates.json("rain")));
            editor(mc.screen).importClipboard();
        });
        context.waitTicks(4);
    }

    /**
     * Proof that a broken widget cannot take the game down: a widget that throws when drawn and when clicked is put
     * on the editor screen. Expected: one log entry per kind (not one per frame), an error box in its place, the rest
     * of the screen still drawing and answering. Returns how many failures it caused.
     */
    private static int canary(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        int before = UiFailures.count();
        Widget[] made = new Widget[2];
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) mc.screen;
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
            UiScreen s = (UiScreen) mc.screen;
            s.mouseClicked(new MouseButtonEvent(60, 80, new MouseButtonInfo(0, 0)), false);
            s.mouseReleased(new MouseButtonEvent(60, 80, new MouseButtonInfo(0, 0)));
            // Clicking it again: a broken widget takes no input, so no second failure.
            s.mouseClicked(new MouseButtonEvent(60, 80, new MouseButtonInfo(0, 0)), false);
            s.mouseReleased(new MouseButtonEvent(60, 80, new MouseButtonInfo(0, 0)));
        });
        context.waitTicks(10);
        shot.accept(name + "_canary_error_boxes");
        int caused = UiFailures.count() - before;
        boolean boxes = context.computeOnClient(mc -> made[0].broken() && made[1].broken());
        // The rest still works: the editor keeps drawing and taking changes.
        context.runOnClient(mc -> editor(mc.screen).doc.set("duration", 33));
        context.waitTicks(3);
        boolean alive = context.computeOnClient(mc -> mc.screen instanceof FxEditorScreen
                && editor(mc.screen).doc.num("duration", 0) == 33 && editor(mc.screen).shown());
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) mc.screen;
            s.rootWidget().children().remove(made[0]);
            s.rootWidget().children().remove(made[1]);
        });
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: canary caused {} failures (kinds now {}), error boxes {}, screen alive {}",
                name, caused, UiFailures.kinds().size(), boxes, alive);
        check.accept(caused == 2, name + ": a failing widget is reported once per kind, not per frame (" + caused + ")");
        check.accept(boxes && alive, name + ": failing widgets become error boxes and the rest keeps working");
        return caused;
    }

    /** NaN and infinite mouse positions, also in the middle of a drag. */
    private static void brokenMouse(ClientGameTestContext context) {
        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e300};
        context.runOnClient(mc -> {
            UiScreen s = (UiScreen) mc.screen;
            for (double v : bad) {
                s.mouseClicked(new MouseButtonEvent(v, v, new MouseButtonInfo(0, 0)), false);
                s.mouseClicked(new MouseButtonEvent(10, v, new MouseButtonInfo(0, 0)), true);
                s.mouseDragged(new MouseButtonEvent(v, 10, new MouseButtonInfo(0, 0)), v, v);
                s.mouseReleased(new MouseButtonEvent(v, v, new MouseButtonInfo(0, 0)));
                s.mouseScrolled(v, v, v, v);
                s.mouseScrolled(10, 10, 0, v);
            }
            // A drag on the timeline that goes off to infinity.
            FxEditor e = editor(s);
            double x = e.x + e.w / 2.0;
            double y = e.y + e.h - 30;
            s.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
            s.mouseDragged(new MouseButtonEvent(Double.POSITIVE_INFINITY, y, new MouseButtonInfo(0, 0)), 1, 0);
            s.mouseDragged(new MouseButtonEvent(x + 5, y, new MouseButtonInfo(0, 0)), 1, 0);
            s.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)));
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
                if (context.computeOnClient(mc -> mc.screen instanceof FxEditorScreen)) {
                    for (Selection part : new Selection[]{Selection.EFFECT, Selection.APPEARANCE,
                            new Selection(Selection.Kind.LAYER, 0), new Selection(Selection.Kind.EMITTER, 0),
                            new Selection(Selection.Kind.SOUND, 0)}) {
                        context.runOnClient(mc -> editor(mc.screen).select(part));
                        context.waitTicks(2);
                        // Every drop-down of the page opened once (where the sample list crashed before).
                        openEveryList(context);
                    }
                    if (size[0] == 1920) {
                        shot.accept(name + "_s" + scale + "_" + size[0] + "x" + size[1]);
                    }
                    context.runOnClient(mc -> ((FxEditorScreen) mc.screen).back());
                    context.waitTicks(2);
                }
                context.runOnClient(mc -> {
                    if (mc.screen != null) {
                        mc.screen.onClose();
                    }
                });
                context.waitTicks(2);
            }
        }
    }

    private static void openEveryList(ClientGameTestContext context) {
        int lists = context.computeOnClient(mc -> dropdowns((UiScreen) mc.screen).size());
        for (int i = 0; i < lists; i++) {
            int index = i;
            context.runOnClient(mc -> {
                UiScreen s = (UiScreen) mc.screen;
                closeOverlays(s);
                List<Dropdown<?>> all = dropdowns(s);
                if (index < all.size()) {
                    all.get(index).openList();
                }
            });
            context.waitTick();
        }
        context.runOnClient(mc -> closeOverlays((UiScreen) mc.screen));
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

    // --- helpers ---

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

    /** Good and broken textures in the library: a mask, a sprite, a bad name, not a PNG, a picture too large. */
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
        // Only a header saying 4000×4000: refused before decoding.
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
                // Test run folder only.
            }
        }
        try (var files = Files.list(FxEffects.EFFECTS)) {
            for (Path p : files.toList()) {
                if (p.getFileName().toString().startsWith("my_")) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException ignored) {
            // No folder.
        }
        try {
            Files.deleteIfExists(FxEffects.EXPORTS.resolve("crawl_fx.shatterfx"));
        } catch (IOException ignored) {
            // Test run folder only.
        }
    }
}
