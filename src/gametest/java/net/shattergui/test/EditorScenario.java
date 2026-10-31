package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;
import net.shattergui.ui.Container;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The settings editor (in Ukrainian): every tab at GUI scales 2, 3 and 4 and the "too small" notice, an open drop-down
 * list, a tooltip under the real mouse, keyboard focus, typing into the screen search, the live preview reacting to a
 * change, the in-world preview with unsaved values, the discard question on Esc, Save, clamping of typed values, and
 * that the editor itself never gets an effect.
 */
final class EditorScenario {
    private EditorScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        setLanguage(context, "uk_ua");
        // Save replaces the live config object; the rest of the test keeps a reference to this one.
        ShatterConfig original = ShatterConfig.get();
        int originalScale = context.computeOnClient(mc -> mc.options.guiScale().get());
        try {
            int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
            context.waitTicks(40);
            int worldFps = context.computeOnClient(Minecraft::getFps);
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} fps in the world without the editor (reference)", name, worldFps);
            context.getInput().setCursorPos(2, 2);
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(5);

            // Every tab at three GUI scales (1920×1080: 960×540, 640×360, 480×270).
            for (int scale : new int[]{2, 3, 4}) {
                setScale(context, scale);
                for (ShatterConfigScreen.Tab tab : ShatterConfigScreen.Tab.values()) {
                    context.runOnClient(mc -> editor(mc).selectTab(tab));
                    context.waitTicks(12);
                    shot.accept(name + "_s" + scale + "_" + tab.name().toLowerCase());
                    if (tab == ShatterConfigScreen.Tab.GENERAL) {
                        for (int i = 0; i < 3; i++) {
                            context.waitTicks(3);
                            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: thumbs {}", name,
                                    context.computeOnClient(mc -> editor(mc).describeThumbs()));
                        }
                    }
                }
            }
            // The game caps the scale to what fits (at least 320×240), so a narrow window shows the notice:
            // 760×960 at scale 2 = 380×480, narrower than the editor's 400.
            setScale(context, 2);
            context.getInput().resizeWindow(760, 960);
            context.waitTicks(4);
            boolean tooSmall = context.computeOnClient(mc -> editor(mc).tooSmall());
            shot.accept(name + "_small_window");
            check.accept(tooSmall, name + ": in a 760×960 window at GUI scale 2 the editor must show the too-small notice");
            context.getInput().resizeWindow(700, 420);
            context.waitTicks(4);
            shot.accept(name + "_window_700x420");
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(4);
            setScale(context, 3);

            // Frame rate on the busiest tab (many animated miniatures).
            context.runOnClient(mc -> editor(mc).selectTab(ShatterConfigScreen.Tab.EFFECTS));
            context.waitTicks(60);
            int fps = context.computeOnClient(Minecraft::getFps);
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} fps on the Effects tab at GUI scale 3", name, fps);

            // A drop-down list open (General: close effect, with animated miniatures).
            context.runOnClient(mc -> editor(mc).selectTab(ShatterConfigScreen.Tab.GENERAL));
            context.waitTicks(4);
            context.runOnClient(mc -> {
                Dropdown<?> list = dropdowns(editor(mc)).get(1);
                editor(mc).setFocus(list);
            });
            context.waitTicks(2);
            context.runOnClient(mc -> dropdowns(editor(mc)).get(1).openList());
            context.waitTicks(10);
            shot.accept(name + "_dropdown_open");
            boolean overlay = context.computeOnClient(mc -> editor(mc).hasOverlay());
            context.getInput().pressKey(InputConstants.KEY_DOWN);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            boolean closedList = context.computeOnClient(mc -> mc.screen instanceof ShatterConfigScreen e && !e.hasOverlay());
            check.accept(overlay && closedList, name + ": a drop-down must open and close with Esc without closing the editor");

            // Real clicks on the header: the first opens, the second closes (it used to reopen with a fresh animation).
            int guiScale = context.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
            int[] header = context.computeOnClient(mc -> {
                Dropdown<?> d = dropdowns(editor(mc)).get(1);
                return new int[]{d.x + d.w / 2, d.y + d.h / 2};
            });
            context.getInput().setCursorPos(header[0] * guiScale, header[1] * guiScale);
            context.waitTicks(2);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(6);
            boolean openedByClick = context.computeOnClient(mc -> dropdowns(editor(mc)).get(1).isOpen());
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(1);
            String afterSecond = context.computeOnClient(mc -> String.valueOf(dropdowns(editor(mc)).get(1).listState()));
            context.waitTicks(6);
            boolean closedByHeader = context.computeOnClient(mc -> !dropdowns(editor(mc)).get(1).isOpen() && !editor(mc).hasOverlay()
                    && editor(mc).overlays().isEmpty());
            check.accept(openedByClick && closedByHeader && !"OPENING".equals(afterSecond) && !"OPEN".equals(afterSecond),
                    name + ": a click on the header of an open list must close it (opened " + openedByClick + ", after the second click "
                            + afterSecond + ", closed " + closedByHeader + ")");
            // Open again, then a click far outside closes it.
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(6);
            context.getInput().setCursorPos(3 * guiScale, 3 * guiScale);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(6);
            check.accept(context.computeOnClient(mc -> !dropdowns(editor(mc)).get(1).isOpen() && editor(mc).overlays().isEmpty()),
                    name + ": a click outside must close the list");
            context.getInput().setCursorPos(5, 5);

            // Tooltip under the real mouse (over the "Effects" tab), then a real click on it.
            int scale = context.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
            int[] tabCenter = context.computeOnClient(mc -> editor(mc).tabCenter(ShatterConfigScreen.Tab.EFFECTS));
            context.getInput().setCursorPos(tabCenter[0] * scale, tabCenter[1] * scale);
            context.waitTicks(20);
            shot.accept(name + "_tooltip");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(4);
            boolean clicked = context.computeOnClient(mc -> editor(mc).tab() == ShatterConfigScreen.Tab.EFFECTS);
            check.accept(clicked, name + ": clicking the Effects tab must open it");
            context.getInput().setCursorPos(5, 5);

            // Keyboard focus.
            context.getInput().pressKey(InputConstants.KEY_TAB);
            context.getInput().pressKey(InputConstants.KEY_TAB);
            context.waitTicks(4);
            boolean focused = context.computeOnClient(mc -> editor(mc).focused() != null);
            shot.accept(name + "_keyboard_focus");
            check.accept(focused, name + ": Tab must focus a widget");

            // Typing into the screens search.
            context.runOnClient(mc -> {
                editor(mc).selectTab(ShatterConfigScreen.Tab.SCREENS);
                editor(mc).focusSearch();
            });
            context.getInput().typeChars("furn");
            context.waitTicks(6);
            String typed = context.computeOnClient(mc -> editor(mc).searchText());
            shot.accept(name + "_screens_search");
            check.accept("furn".equals(typed), name + ": typed text must reach the search field, got '" + typed + "'");
            context.runOnClient(mc -> editor(mc).setSearch(""));

            // Clamping: typed nonsense never reaches an effect.
            ShatterConfig scratch = ShatterConfig.get().copy();
            float huge = scratch.set(Params.GLASS_GRAVITY, "1e9");
            float junk = scratch.set(Params.GLASS_GRAVITY, "abc");
            int negative = scratch.set(Params.BURN_DURATION, -50);
            check.accept(huge == 0.3f && junk == 0.04f && negative == 5,
                    name + ": values must be clamped/validated (" + huge + ", " + junk + ", " + negative + ")");

            // The live preview restarts on a change and plays the burn with the unsaved values.
            context.runOnClient(mc -> {
                ShatterConfigScreen editor = editor(mc);
                editor.selectTab(ShatterConfigScreen.Tab.EFFECTS);
                editor.selectEffect("burn");
                editor.working().set(Params.BURN_DURATION, 15);
                editor.working().set(Params.BURN_SMOKE_DURATION, 20);
            });
            context.waitTicks(30);
            String livePhase = context.computeOnClient(mc -> editor(mc).describePreview());
            shot.accept(name + "_live_burn_t30");
            context.waitTicks(10);
            shot.accept(name + "_live_burn_t40");
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: live preview {}", name, livePhase);

            // In-world preview with the unsaved burn; the editor comes back with the edit still there.
            context.runOnClient(mc -> editor(mc).preview());
            context.waitTicks(10);
            shot.accept(name + "_world_preview_t10");
            boolean previewRunning = context.computeOnClient(mc -> ShardManager.hasPreview());
            context.waitFor(mc -> mc.screen instanceof ShatterConfigScreen, 200);
            context.waitTicks(2);
            int kept = context.computeOnClient(mc -> editor(mc).working().get(Params.BURN_DURATION));
            int live = context.computeOnClient(mc -> ShatterConfig.get().get(Params.BURN_DURATION));
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: world preview running={} unsaved kept={} live={}", name,
                    previewRunning, kept, live);
            check.accept(previewRunning, name + ": the in-world preview must play an effect");
            check.accept(kept == 15 && live == 30, name + ": unsaved value kept in the editor, live config untouched");

            // Esc with unsaved changes asks first; Enter discards.
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(8);
            boolean asked = context.computeOnClient(mc -> mc.screen instanceof ShatterConfigScreen e && e.hasOverlay());
            shot.accept(name + "_discard_dialog");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(3);
            boolean closed = context.computeOnClient(mc -> mc.screen == null);
            int afterCancel = context.computeOnClient(mc -> ShatterConfig.get().get(Params.BURN_DURATION));
            check.accept(asked, name + ": Esc with unsaved changes must ask first");
            check.accept(closed && afterCancel == 30, name + ": discarding must close without saving");

            // Save.
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> {
                editor(mc).working().set(Params.GLASS_SPIN, 0.5f);
                editor(mc).save();
            });
            context.waitTicks(3);
            float saved = context.computeOnClient(mc -> ShatterConfig.get().get(Params.GLASS_SPIN));
            check.accept(saved == 0.5f, name + ": Save must apply the change, spin=" + saved);

            int closesAfter = context.computeOnClient(mc -> ShatterProfiler.closeCount());
            check.accept(closesAfter == closes, name + ": the editor itself must never get an effect ("
                    + (closesAfter - closes) + " effects)");
        } finally {
            context.runOnClient(mc -> ShatterConfig.replace(original));
            setScale(context, originalScale);
            setLanguage(context, "en_us");
        }
    }

    static ShatterConfigScreen editor(Minecraft mc) {
        if (!(mc.screen instanceof ShatterConfigScreen screen)) {
            throw new IllegalStateException("editor not open: " + mc.screen);
        }
        return screen;
    }

    /** Every drop-down in the editor, in layout order. */
    static List<Dropdown<?>> dropdowns(UiScreen screen) {
        List<Dropdown<?>> out = new ArrayList<>();
        collect(screen.rootWidget(), out);
        return out;
    }

    private static void collect(Widget widget, List<Dropdown<?>> out) {
        if (widget instanceof Dropdown<?> dropdown) {
            out.add(dropdown);
        }
        if (widget instanceof ScrollView scroll) {
            collect(scroll.content(), out);
        }
        if (widget instanceof Container container) {
            for (Widget child : container.children()) {
                collect(child, out);
            }
        }
    }

    static void setScale(ClientGameTestContext context, int scale) {
        context.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        context.waitTicks(2);
    }

    static void setLanguage(ClientGameTestContext context, String code) {
        context.runOnClient(mc -> {
            mc.getLanguageManager().setSelected(code);
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        });
        context.waitTicks(2);
    }
}
