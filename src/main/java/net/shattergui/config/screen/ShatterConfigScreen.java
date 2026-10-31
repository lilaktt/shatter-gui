package net.shattergui.config.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.shattergui.ShatterKeys;
import net.shattergui.ScreenRules;
import net.shattergui.config.ScreenCatalog;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.Themes;
import net.shattergui.custom.CustomEffect;
import net.shattergui.custom.CustomEffectDef;
import net.shattergui.custom.CustomEffects;
import net.shattergui.effect.BurnEffect;
import net.shattergui.effect.GlassEffect;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.editor.FxEditor;
import net.shattergui.editor.FxEditorScreen;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.params.Param;
import net.shattergui.params.ParamStore;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.params.Preset;
import net.shattergui.params.Quality;
import net.shattergui.ui.Button;
import net.shattergui.ui.Card;
import net.shattergui.ui.Column;
import net.shattergui.ui.ConfirmDialog;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Grid;
import net.shattergui.ui.Hint;
import net.shattergui.ui.Section;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Label;
import net.shattergui.ui.Row;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Slider;
import net.shattergui.ui.Swatch;
import net.shattergui.ui.TabNav;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Segmented;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Toggle;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiClock;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;
import net.shattergui.ui.preview.PreviewPlayer;
import net.shattergui.ui.preview.RealPreview;
import net.shattergui.ui.preview.Scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The mod's settings, drawn with the mod's own widgets ({@code net.shattergui.ui}). Side navigation on the left
 * (General, Effects, Screens, Custom), the settings in the middle, and on the right a live preview that plays the
 * chosen effect on a sample interface with the current, unsaved values (replayed on every change while "autoplay" is
 * on; "Replay" plays it again; "In the world" plays the real effect).
 *
 * <p>Works on a copy of the config: Save replaces the live config and writes it (custom effects too, if they
 * changed); Cancel or Esc drops the copy, after asking if anything changed. The screen never gets an effect itself.
 */
public final class ShatterConfigScreen extends UiScreen {
    public enum Tab {
        GENERAL, EFFECTS, SCREENS, CUSTOM
    }

    private static final String DEFAULT = "default";
    private static final String OFF = "off";
    private static final String ALL_MODS = "*";
    private static final Set<String> NO_EDGE = Set.of("ChatScreen", "InBedChatScreen", "SignEditScreen",
            "HangingSignEditScreen", "BookViewScreen", "BookEditScreen", "BookSignScreen", "CommandBlockEditScreen",
            "MinecartCommandBlockEditScreen", "JigsawBlockEditScreen", "StructureBlockEditScreen");
    private static final List<String> OPEN_IDS = List.of(ShatterConfig.OPEN_NONE, ShatterConfig.OPEN_FLY_FROM_BLOCK);

    private final Screen parent;
    private final ShatterConfig work;
    private final String startJson;
    private final List<CustomEffectDef> customs = new ArrayList<>();
    private final List<String> customsAtStart = new ArrayList<>();
    private final String savedAccent;

    private Tab tab = Tab.GENERAL;
    /** Which way the big preview plays the effect selected on the Effects tab. */
    private enum PreviewMode {
        OPEN, CLOSE, BOTH
    }

    private PreviewMode previewMode = PreviewMode.BOTH;
    private String selectedEffect;
    private String selectedCustom;
    private String cloneFrom = "vortex";
    /** Set by "Copy to my effects": open the editor on the whole screen if it has no room in the tab. */
    private boolean fullscreenIfSmall;
    private String selectedScreen;
    private String modFilter = ALL_MODS;
    private Component status = Component.empty();
    private int statusColor = Theme.SUCCESS;
    private double statusTime = -10;
    private boolean dirty;
    private int dirtyCountdown;
    private ParamValues frameValues;

    // --- widgets ---
    private final TabNav nav;
    private final Column content = new Column(0, 8);
    private final ScrollView scroll = new ScrollView(content);
    private final Column screenList = new Column(0, 6);
    private final TextField search = new TextField(40);
    private final PreviewPlayer live;
    private final Map<String, PreviewPlayer> thumbs = new HashMap<>();
    private final Toggle autoplay;
    private final Button replay;
    private final Button inWorld;
    private final Button save;
    private final Button cancel;
    private final LiveBox liveBox = new LiveBox();
    private final Segmented previewModes = new Segmented();
    private final Decor decor = new Decor();
    /** The schema-2 effect editor of the Custom tab (made when first shown). */
    private FxEditor fxEditor;
    /** The Custom tab shows the classic (format 1) custom effects instead of the editor. */
    private boolean classicCustom;
    private final SmallNotice smallNotice = new SmallNotice();

    // --- layout (set every frame) ---
    private int margin;
    private int headerBottom;
    private int footerTop;
    private int sideX;
    private int sideW;
    private int previewX;
    private int previewW;
    private int bodyTop;
    private int bodyBottom;
    private List<net.minecraft.util.FormattedCharSequence> captionLines = List.of();

    public ShatterConfigScreen(Screen parent) {
        super(Component.translatable("screen.shatter_gui.title"));
        this.parent = parent;
        this.work = ShatterConfig.get().copy();
        this.startJson = work.toJsonString();
        this.savedAccent = ShatterConfig.get().uiAccent;
        Theme.setAccent(Theme.Accent.byId(work.uiAccent));
        RealPreview.setConfigured(work.realPreview);
        for (CustomEffectDef def : CustomEffects.fromConfig()) {
            customs.add(def.copy(def.id(), def.name()));
            customsAtStart.add(def.toJsonString());
        }
        this.selectedCustom = customs.isEmpty() ? null : customs.getFirst().id();
        this.selectedEffect = known(work.effect);
        this.frameValues = work.values();

        nav = new TabNav(true, i -> selectTab(Tab.values()[i]));
        nav.add(Icon.GEAR, tr("ui.shatter_gui.tab.general"), tr("ui.shatter_gui.tab.general.desc"))
                .add(Icon.SPARKLES, tr("ui.shatter_gui.tab.effects"), tr("ui.shatter_gui.tab.effects.desc"))
                .add(Icon.SCREENS, tr("ui.shatter_gui.tab.screens"), tr("ui.shatter_gui.tab.screens.desc"))
                .add(Icon.STAR, tr("ui.shatter_gui.tab.custom"), tr("ui.shatter_gui.tab.custom.desc"));

        live = new PreviewPlayer(this::liveScene, true, work.uiAutoplay);
        live.autoRestart(work.uiAutoplay);
        autoplay = new Toggle(() -> work.uiAutoplay, v -> {
            work.uiAutoplay = v;
            live.loop(v);
            live.autoRestart(v);
        });
        autoplay.tooltip(tr("ui.shatter_gui.autoplay"), tr("ui.shatter_gui.autoplay.tooltip"));
        replay = new Button(tr("ui.shatter_gui.replay"), Icon.REPLAY, Button.Kind.SECONDARY, () -> live.restart());
        replay.tooltip(tr("ui.shatter_gui.replay"), tr("ui.shatter_gui.replay.tooltip"));
        inWorld = new Button(tr("ui.shatter_gui.in_world"), Icon.EYE, Button.Kind.GHOST, this::preview);
        inWorld.tooltip(tr("ui.shatter_gui.in_world"), tr("ui.shatter_gui.in_world.tooltip"));
        inWorld.leavesScreen(true);
        previewModes.option(tr("ui.shatter_gui.preview.mode.open"), tr("ui.shatter_gui.preview.mode.open.desc"),
                () -> previewMode == PreviewMode.OPEN, () -> setPreviewMode(PreviewMode.OPEN));
        previewModes.option(tr("ui.shatter_gui.preview.mode.close"), tr("ui.shatter_gui.preview.mode.close.desc"),
                () -> previewMode == PreviewMode.CLOSE, () -> setPreviewMode(PreviewMode.CLOSE));
        previewModes.option(tr("ui.shatter_gui.preview.mode.both"), tr("ui.shatter_gui.preview.mode.both.desc"),
                () -> previewMode == PreviewMode.BOTH, () -> setPreviewMode(PreviewMode.BOTH));
        save = new Button(tr("ui.shatter_gui.save"), Icon.SAVE, Button.Kind.PRIMARY, this::save).leavesScreen(true);
        cancel = new Button(tr("gui.cancel"), null, Button.Kind.SECONDARY, this::escape).leavesScreen(true);
        save.tooltip(tr("ui.shatter_gui.save"), tr("ui.shatter_gui.save.tooltip"));
        cancel.tooltip(tr("gui.cancel"), tr("ui.shatter_gui.cancel.tooltip"));

        search.icon(Icon.SEARCH).hint(tr("ui.shatter_gui.screens.search")).onChange(s -> rebuildScreenList());
        search.tooltip(tr("ui.shatter_gui.screens.search"), tr("ui.shatter_gui.screens.search.tooltip"));

        root.add(decor);
        root.add(nav);
        root.add(scroll);
        root.add(liveBox);
        root.add(previewModes);
        root.add(autoplay);
        root.add(replay);
        root.add(inWorld);
        root.add(cancel);
        root.add(save);
        root.add(smallNotice);
        rebuild();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    // --- public API (tests, other code) ---

    /** The settings being edited. */
    public ShatterConfig working() {
        return work;
    }

    public List<CustomEffectDef> customs() {
        return customs;
    }

    public CustomEffectDef selectedCustom() {
        return custom(selectedCustom);
    }

    public Component status() {
        return status;
    }

    public Tab tab() {
        return tab;
    }

    public boolean dirty() {
        return computeDirty();
    }

    /** What every miniature is doing (for tests and debugging). */
    public String describeThumbs() {
        StringBuilder sb = new StringBuilder();
        thumbs.forEach((key, player) -> sb.append(key).append('=').append(player.describe()).append("; "));
        return sb.toString();
    }

    /** What the live preview is doing (for tests). */
    public String describePreview() {
        return live.describe();
    }

    public void selectTab(Tab newTab) {
        boolean changed = newTab != tab;
        tab = newTab;
        nav.select(newTab.ordinal(), true);
        if (changed) {
            scroll.scrollToTop();
            setFocus(null);
        }
        rebuild();
    }

    /** Scrolls the settings to the end (tests, screenshots). */
    public void scrollToEnd() {
        scroll.scrollToEnd();
    }

    public void selectEffect(String id) {
        selectedEffect = known(id);
        rebuild();
    }

    public void selectCustom(String id) {
        if (custom(id) != null) {
            selectedCustom = id;
        }
        rebuild();
    }

    public void selectScreen(String key) {
        selectedScreen = key;
    }

    public void setCloneFrom(String id) {
        cloneFrom = known(id);
    }

    /** Centre of a navigation tab in GUI coordinates (tests move the mouse there). */
    public int[] tabCenter(Tab target) {
        return nav.center(target.ordinal());
    }

    /** Gives the keyboard focus to the screen search field (tests type into it). */
    public void focusSearch() {
        setFocus(search);
    }

    public String searchText() {
        return search.value();
    }

    public void setSearch(String text) {
        search.value(text);
        rebuildScreenList();
    }

    // --- layout ---

    @Override
    protected void layoutRoot(int width, int height) {
        frameValues = work.values();
        margin = width < 520 ? 4 : 8;
        boolean compact = width < 600;
        nav.compact(compact);
        sideW = compact ? 28 : 118;
        previewW = width < 560 ? Math.max(118, width / 4) : Math.clamp(Math.round(width * 0.27f), 150, 260);
        headerBottom = margin + 26;
        footerTop = height - margin - 26;
        bodyTop = headerBottom + 4;
        bodyBottom = footerTop - 4;
        sideX = margin + 6;
        nav.layout(sideX, bodyTop + 4, sideW);
        int contentX = sideX + sideW + 8;
        previewX = width - margin - 6 - previewW;
        int contentW = previewX - 8 - contentX;
        scroll.setBounds(contentX, bodyTop, contentW, bodyBottom - bodyTop);
        // Preview panel: title, box, caption, controls.
        int boxX = previewX + 6;
        int boxW = previewW - 12;
        int controlsH = Theme.CONTROL_HEIGHT + 20 + 16;
        // The Effects tab also chooses which way the preview plays (up to two lines of buttons).
        previewModes.visible = tab == Tab.EFFECTS && !editorMode();
        if (previewModes.visible) {
            controlsH += (Theme.CONTROL_HEIGHT + 3) * 2 + 4;
        }
        int boxH = Math.max(40, Math.min(Math.round(boxW * 0.9f), bodyBottom - bodyTop - 24 - controlsH));
        liveBox.setBounds(boxX, bodyTop + 18, boxW, boxH);
        // The caption under the preview wraps onto a second line rather than being cut.
        captionLines = font.split(previewCaption(), Math.max(20, boxW)).stream().limit(2).toList();
        int cy = liveBox.y + boxH + 6 + captionLines.size() * Theme.LINE;
        if (previewModes.visible) {
            previewModes.layout(boxX, cy, boxW);
            cy += previewModes.h + 6;
        }
        autoplay.setBounds(previewX + previewW - 6 - Toggle.WIDTH, cy, Toggle.WIDTH, 14);
        cy += 20;
        int half = (boxW - 4) / 2;
        // Narrow panel: icons only (the tooltips still name them).
        boolean iconsOnly = half < 82;
        replay.text(iconsOnly ? null : tr("ui.shatter_gui.replay"));
        inWorld.text(iconsOnly ? null : tr("ui.shatter_gui.in_world"));
        replay.setBounds(boxX, cy, half, Theme.CONTROL_HEIGHT);
        inWorld.setBounds(boxX + half + 4, cy, boxW - half - 4, Theme.CONTROL_HEIGHT);
        inWorld.enabled = minecraft != null && minecraft.level != null;
        // Footer.
        int saveW = Math.max(save.naturalWidth(), 72);
        save.setBounds(width - margin - 6 - saveW, footerTop + 4, saveW, Theme.CONTROL_HEIGHT);
        int cancelW = Math.max(cancel.naturalWidth(), 72);
        cancel.setBounds(save.x - 6 - cancelW, footerTop + 4, cancelW, Theme.CONTROL_HEIGHT);
        decor.setBounds(0, 0, width, height);
        layoutEditor(contentX, width);
        if (--dirtyCountdown <= 0) {
            dirty = computeDirty();
            dirtyCountdown = 10;
        }
    }

    /** The backdrop, window, header, side bar, preview panel and footer text. */
    private final class Decor extends Widget {
        @Override
        protected void draw(Ui ui) {
            int w = width;
            int h = height;
            ui.g.fillGradient(0, 0, w, h, Theme.BACKDROP_TOP, Theme.BACKDROP_BOTTOM);
            int wx = margin;
            int wy = margin;
            int ww = w - margin * 2;
            int wh = h - margin * 2;
            Draw.shadow(ui, wx, wy, ww, wh, 8, 1f);
            Draw.roundBox(ui, wx, wy, ww, wh, 8, Theme.PANEL, Theme.BORDER);
            // Header: logo, title, subtitle.
            Draw.roundGradient(ui, wx + 8, wy + 5, 16, 16, 4, Theme.accentHover(), Theme.accentPressed());
            Draw.icon(ui, Icon.SPARKLES, wx + 12, wy + 9, 1, 0xFFFFFFFF);
            ui.text(tr("ui.shatter_gui.title"), wx + 30, wy + 6, Theme.TEXT);
            if (w >= 480) {
                ui.textSmall(tr("ui.shatter_gui.subtitle").getString(), wx + 30, wy + 16, ww / 2, Theme.TEXT_DIM);
            }
            Draw.rect(ui, wx + 1, headerBottom, ww - 2, 1, Theme.BORDER);
            // Side bar.
            Draw.roundRect(ui, sideX - 3, bodyTop, sideW + 6, bodyBottom - bodyTop, 6, Theme.SIDEBAR);
            // Preview panel (not with the effect editor, which has its own).
            if (!editorMode()) {
                Draw.roundBox(ui, previewX, bodyTop, previewW, bodyBottom - bodyTop, 6, Theme.CARD, Theme.BORDER);
                Draw.icon(ui, Icon.PLAY, previewX + 6, bodyTop + 5, 1, Theme.accentHover());
                ui.textFit(tr("ui.shatter_gui.preview"), previewX + 18, bodyTop + 6, previewW - 24, Theme.TEXT);
                int capY = liveBox.y + liveBox.h + 4;
                for (var line : captionLines) {
                    ui.g.text(ui.font, line, previewX + 6, capY, Theme.TEXT_DIM, false);
                    capY += Theme.LINE;
                }
                ui.textFit(tr("ui.shatter_gui.autoplay"), previewX + 6, autoplay.y + 3, previewW - 18 - Toggle.WIDTH, Theme.TEXT);
            }
            // Footer: unsaved changes, status.
            Draw.rect(ui, wx + 1, footerTop, ww - 2, 1, Theme.BORDER);
            int fy = footerTop + 9;
            double since = UiClock.time() - statusTime;
            if (since < 4.5) {
                float a = (float) Math.min(1, Math.min(since * 6, (4.5 - since) * 2));
                ui.textFit(status, wx + 10, fy, cancel.x - wx - 16, Theme.fade(statusColor, a));
            } else if (dirty) {
                Draw.circle(ui, wx + 12, fy + 3, 2, Theme.WARNING);
                ui.textFit(tr("ui.shatter_gui.unsaved"), wx + 18, fy, cancel.x - wx - 24, Theme.TEXT_DIM);
            }
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    /** The big live preview. */
    private final class LiveBox extends Widget {
        @Override
        protected void draw(Ui ui) {
            Draw.roundRect(ui, x - 1, y - 1, w + 2, h + 2, 4, Theme.BORDER_STRONG);
            ui.pushClip(x, y, w, h);
            live.render(ui, x, y, w, h);
            if (tab == Tab.EFFECTS && previewMode == PreviewMode.OPEN && openValue(selectedEffect) == null) {
                // Nothing to play: say why instead of an empty box.
                ui.g.fill(x, y, x + w, y + h, 0xC0101218);
                var lines = ui.font.split(tr("ui.shatter_gui.preview.no_open", effectName(selectedEffect)), Math.max(20, w - 12));
                int ty = y + Math.max(4, (h - lines.size() * Theme.LINE) / 2);
                for (var line : lines) {
                    ui.g.text(ui.font, line, x + 6, ty, Theme.TEXT, false);
                    ty += Theme.LINE;
                }
            }
            ui.popClip();
        }
    }

    // --- the effect editor (Custom tab) ---

    /** The Custom tab shows the schema-2 effect editor (not the classic custom effects). */
    private boolean editorMode() {
        return tab == Tab.CUSTOM && !classicCustom;
    }

    /** The effect editor (made on first use). */
    public FxEditor effectEditor() {
        if (fxEditor == null) {
            fxEditor = new FxEditor(new EditorHost(), () -> work.values());
            root.add(fxEditor);
        }
        return fxEditor;
    }

    public void setClassicCustom(boolean value) {
        classicCustom = value;
        scroll.scrollToTop();
        rebuild();
    }

    public boolean classicCustom() {
        return classicCustom;
    }

    /** Places the editor (or, if there is too little room, a notice with "full screen") in the Custom tab. */
    private void layoutEditor(int contentX, int width) {
        boolean on = editorMode();
        scroll.visible = !on;
        liveBox.visible = !on;
        autoplay.visible = !on;
        replay.visible = !on;
        inWorld.visible = !on;
        if (!on) {
            if (fxEditor != null) {
                fxEditor.visible = false;
            }
            smallNotice.visible = false;
            return;
        }
        FxEditor editor = effectEditor();
        int ex = contentX;
        int ew = width - margin - 6 - ex;
        int eh = bodyBottom - bodyTop;
        boolean fits = ew >= 440 && eh >= 220;
        if (fullscreenIfSmall) {
            // A copy was just opened to edit: without room here it opens on the whole screen.
            fullscreenIfSmall = false;
            if (!fits) {
                minecraft.execute(this::openEditorFullscreen);
            }
        }
        editor.visible = fits;
        smallNotice.visible = !fits;
        if (fits) {
            editor.setBounds(ex, bodyTop, ew, eh);
        } else {
            smallNotice.setBounds(ex, bodyTop, ew, eh);
        }
    }

    /** Puts the editor back after full screen. */
    private void reattachEditor() {
        if (fxEditor != null) {
            root.children().remove(fxEditor);
            root.add(fxEditor);
        }
    }

    private void disposeEditor() {
        if (fxEditor != null) {
            fxEditor.dispose();
        }
        // The real screens' pictures are only for this editor: their GPU memory goes back now.
        RealPreview.release();
    }

    /** The setting (unsaved until Save); the previews follow it at once. */
    private void setRealPreview(boolean value) {
        work.realPreview = value;
        RealPreview.setConfigured(value);
    }

    /** Opens the editor on the whole screen. */
    public void openEditorFullscreen() {
        minecraft.setScreen(new FxEditorScreen(this, effectEditor(), this::reattachEditor));
    }

    @Override
    protected boolean shortcut(KeyEvent event) {
        return editorMode() && fxEditor != null && fxEditor.visible && fxEditor.shortcut(event);
    }

    private final class EditorHost implements FxEditor.Host {
        @Override
        public boolean fullscreen() {
            return minecraft != null && minecraft.screen instanceof FxEditorScreen;
        }

        @Override
        public void toggleFullscreen() {
            if (minecraft.screen instanceof FxEditorScreen screen) {
                screen.back();
            } else {
                openEditorFullscreen();
            }
        }

        @Override
        public boolean canShowClassic() {
            return !fullscreen();
        }

        @Override
        public void showClassic() {
            setClassicCustom(true);
        }
    }

    /** Too little room for the editor inside the settings: a short note and "full screen". */
    private final class SmallNotice extends net.shattergui.ui.Column {
        SmallNotice() {
            super(12, 8);
            add(new Label(tr("ui.shatter_gui.fx.small.title"), Label.Style.HEADING));
            add(new Label(tr("ui.shatter_gui.fx.small.desc"), Label.Style.DIM));
            Row row = add(new Row(6));
            Button full = new Button(tr("ui.shatter_gui.fx.top.fullscreen"), Icon.EXPAND, Button.Kind.PRIMARY,
                    ShatterConfigScreen.this::openEditorFullscreen).leavesScreen(true);
            full.tooltip(tr("ui.shatter_gui.fx.top.fullscreen"), tr("ui.shatter_gui.fx.top.fullscreen.desc"));
            row.fixed(full, full.naturalWidth());
            Button classic = new Button(tr("ui.shatter_gui.fx.top.classic"), Icon.STAR, Button.Kind.SECONDARY,
                    () -> setClassicCustom(true));
            classic.tooltip(tr("ui.shatter_gui.fx.top.classic"), tr("ui.shatter_gui.fx.top.classic.desc"));
            row.fixed(classic, classic.naturalWidth());
            visible = false;
        }

        @Override
        protected void arrange() {
            measure(w);
        }

        @Override
        protected void drawBackground(Ui ui) {
            Draw.roundBox(ui, x, y, w, h, 6, Theme.CARD, Theme.BORDER);
        }
    }

    // --- content ---

    private void rebuild() {
        Widget focused = focused();
        content.clear();
        switch (tab) {
            case GENERAL -> buildGeneral();
            case EFFECTS -> buildEffects();
            case SCREENS -> buildScreens();
            case CUSTOM -> buildCustom();
        }
        if (focused != null && !isAttached(focused)) {
            setFocus(null);
        }
    }

    private boolean isAttached(Widget widget) {
        List<Widget> all = new ArrayList<>();
        root.collectFocusable(all);
        return all.contains(widget);
    }

    /** A card of settings: icon and title in its header (folds, remembered for the session), the explanation as a hint. */
    private Section section(Component title, Component subtitle) {
        String key = title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey()
                : title.getString();
        Section card = new Section("settings." + key, sectionIcon(key), title, false, 7);
        if (subtitle != null) {
            card.add(new Hint(subtitle));
        }
        content.add(card);
        return card;
    }

    private static Icon sectionIcon(String key) {
        return switch (key) {
            case "ui.shatter_gui.general.title" -> Icon.GEAR;
            case "ui.shatter_gui.open.title" -> Icon.IMPORT;
            case "ui.shatter_gui.close.title" -> Icon.EXPORT;
            case "ui.shatter_gui.preset.title" -> Icon.STAR;
            case "ui.shatter_gui.playback.title" -> Icon.REPLAY;
            case "ui.shatter_gui.performance.title" -> Icon.BOLT;
            case "ui.shatter_gui.access.title" -> Icon.EYE;
            case "ui.shatter_gui.where.title" -> Icon.WINDOW;
            case "ui.shatter_gui.look.title" -> Icon.PALETTE;
            case "ui.shatter_gui.effects.title" -> Icon.SPARKLES;
            case "ui.shatter_gui.screens.title" -> Icon.SCREENS;
            case "ui.shatter_gui.custom.title", "ui.shatter_gui.fx.classic.title" -> Icon.STAR;
            case "ui.shatter_gui.data.title" -> Icon.LAYERS;
            case "ui.shatter_gui.about.title" -> Icon.USER;
            default -> Icon.INFO;
        };
    }

    private void buildGeneral() {
        Section master = section(tr("ui.shatter_gui.general.title"), tr("ui.shatter_gui.general.desc"));
        master.add(new SettingRow(tr("ui.shatter_gui.enabled"), tr("ui.shatter_gui.enabled.desc"), true,
                new Toggle(() -> work.enabled, v -> work.enabled = v), () -> !work.enabled, () -> work.enabled = true));

        // When you open an interface.
        Section open = section(tr("ui.shatter_gui.open.title"), tr("ui.shatter_gui.open.desc"));
        open.add(choiceRow(thumb("general-open", () -> openScene(work.openEffect)),
                () -> openName(work.openEffect), () -> openDescription(work.openEffect),
                (Widget) new Dropdown<>(this::openOptions, () -> work.openEffect, v -> work.openEffect = v).thumbs(30, 22)
                        .tooltip(tr("ui.shatter_gui.open.title"), tr("ui.shatter_gui.open.desc"))));

        // When you close an interface.
        Section close = section(tr("ui.shatter_gui.close.title"), tr("ui.shatter_gui.close.desc"));
        close.add(choiceRow(thumb("general-close", () -> closeScene(known(work.effect), null, true, true)),
                () -> effectName(known(work.effect)), () -> effectDescription(known(work.effect)),
                (Widget) new Dropdown<>(this::closeOptions, () -> known(work.effect), v -> work.effect = v).thumbs(30, 22)
                        .tooltip(tr("ui.shatter_gui.close.title"), tr("ui.shatter_gui.close.desc"))));
        close.add(new SettingRow(tr("ui.shatter_gui.thematic"), tr("ui.shatter_gui.thematic.desc"), false,
                new Toggle(() -> work.thematic, v -> work.thematic = v), () -> work.thematic, () -> work.thematic = false));

        // Presets as cards.
        Section presets = section(tr("ui.shatter_gui.preset.title"), tr("ui.shatter_gui.preset.desc"));
        Grid grid = presets.add(new Grid(92, 6));
        for (Preset preset : Preset.values()) {
            // A preset brings its quality and number of effects at once (Performance), which can be changed after.
            Card card = new Card(5, 4).onClick(() -> work.applyPreset(preset)).selected(() -> work.preset.equals(preset.id()));
            card.add(new Thumb(thumb("preset-" + preset.id(), () -> {
                Scene base = closeScene(known(work.effect), null, true, true);
                return new Scene(null, base.base(), base.params().withPreset(preset), base.custom(), true, true);
            }), 0.62f, 70));
            card.add(new Label(tr("ui.shatter_gui.preset." + preset.id()), Label.Style.BODY));
            card.add(new Label(tr("ui.shatter_gui.preset." + preset.id() + ".desc"), Label.Style.DIM));
            card.tooltip(tr("ui.shatter_gui.preset." + preset.id()), tr("ui.shatter_gui.preset." + preset.id() + ".desc"));
            grid.add(card);
        }

        Section playback = section(tr("ui.shatter_gui.playback.title"), null);
        playback.add(paramRow(Params.EFFECT_SPEED, work));
        playback.add(paramRow(Params.OPEN_SPEED, work));
        playback.add(paramRow(Params.SOUND_VOLUME, work));
        playback.add(new SettingRow(tr("ui.shatter_gui.always_on_top"), tr("ui.shatter_gui.always_on_top.desc"), false,
                new Toggle(() -> work.alwaysOnTop, v -> work.alwaysOnTop = v), () -> !work.alwaysOnTop,
                () -> work.alwaysOnTop = true));

        Section performance = section(tr("ui.shatter_gui.performance.title"), tr("ui.shatter_gui.performance.desc"));
        performance.add(new SettingRow(tr("ui.shatter_gui.quality"), tr("ui.shatter_gui.quality.desc"), false,
                new Dropdown<>(() -> {
                    List<Dropdown.Option<Quality>> options = new ArrayList<>();
                    for (Quality quality : Quality.values()) {
                        options.add(new Dropdown.Option<>(quality, tr("ui.shatter_gui.quality." + quality.id()),
                                tr("ui.shatter_gui.quality." + quality.id() + ".desc"), null));
                    }
                    return options;
                }, work::quality, v -> work.effectQuality = v.id()),
                () -> work.quality() != Preset.byId(work.preset).quality(),
                () -> work.effectQuality = Preset.byId(work.preset).quality().id()));
        performance.add(new SettingRow(tr("ui.shatter_gui.max_concurrent"), tr("ui.shatter_gui.max_concurrent.desc"), false,
                new Slider(1, ShatterConfig.MAX_CONCURRENT_LIMIT, 1, work::maxConcurrent,
                        v -> work.maxConcurrentEffects = (int) Math.round(v), v -> Integer.toString((int) Math.round(v)),
                        text -> {
                            try {
                                return (double) Integer.parseInt(text.trim());
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }),
                () -> work.maxConcurrent() != Preset.byId(work.preset).maxConcurrent(),
                () -> work.maxConcurrentEffects = Preset.byId(work.preset).maxConcurrent()));
        performance.add(new SettingRow(tr("ui.shatter_gui.policy"), tr("ui.shatter_gui.policy.desc"), false,
                new Dropdown<>(() -> List.of(
                        new Dropdown.Option<>(ShatterConfig.POLICY_FINISH_OLDEST, tr("ui.shatter_gui.policy.finish_oldest"),
                                tr("ui.shatter_gui.policy.finish_oldest.desc"), null),
                        new Dropdown.Option<>(ShatterConfig.POLICY_SKIP_NEW, tr("ui.shatter_gui.policy.skip_new"),
                                tr("ui.shatter_gui.policy.skip_new.desc"), null)),
                        () -> work.concurrentPolicy, v -> work.concurrentPolicy = v),
                () -> !ShatterConfig.POLICY_FINISH_OLDEST.equals(work.concurrentPolicy),
                () -> work.concurrentPolicy = ShatterConfig.POLICY_FINISH_OLDEST));
        performance.add(paramRow(Params.PARTICLE_DENSITY, work));
        performance.add(paramRow(Params.MAX_SHARDS, work));

        Section access = section(tr("ui.shatter_gui.access.title"), null);
        access.add(new SettingRow(tr("ui.shatter_gui.reduce_motion"), tr("ui.shatter_gui.reduce_motion.desc"), true,
                new Toggle(() -> work.reduceMotion, v -> work.reduceMotion = v), () -> work.reduceMotion,
                () -> work.reduceMotion = false));

        Section where = section(tr("ui.shatter_gui.where.title"), null);
        where.add(new SettingRow(tr("ui.shatter_gui.apply_to"), tr("ui.shatter_gui.apply_to.desc"), false,
                new Dropdown<>(() -> List.of(
                        new Dropdown.Option<>(ShatterConfig.APPLY_ALL, tr("ui.shatter_gui.apply_to.all"),
                                tr("ui.shatter_gui.apply_to.all.desc"), null),
                        new Dropdown.Option<>(ShatterConfig.APPLY_CONTAINERS, tr("ui.shatter_gui.apply_to.containers"),
                                tr("ui.shatter_gui.apply_to.containers.desc"), null)),
                        () -> work.applyTo, v -> work.applyTo = v),
                () -> !ShatterConfig.APPLY_ALL.equals(work.applyTo), () -> work.applyTo = ShatterConfig.APPLY_ALL));

        Section look = section(tr("ui.shatter_gui.look.title"), tr("ui.shatter_gui.look.desc"));
        look.add(new SettingRow(tr("ui.shatter_gui.real_preview"), tr("ui.shatter_gui.real_preview.desc"), false,
                new Dropdown<>(() -> List.of(
                        new Dropdown.Option<>(true, tr("ui.shatter_gui.real_preview.real"),
                                tr("ui.shatter_gui.real_preview.real.desc"), null),
                        new Dropdown.Option<>(false, tr("ui.shatter_gui.real_preview.simple"),
                                tr("ui.shatter_gui.real_preview.simple.desc"), null)),
                        () -> work.realPreview, this::setRealPreview),
                () -> !work.realPreview, () -> setRealPreview(true)));
        Row swatches = look.add(new Row(6));
        for (Theme.Accent accent : Theme.Accent.values()) {
            swatches.fixed(new Swatch(accent.rgb, tr("ui.shatter_gui.accent." + accent.id()),
                    () -> work.uiAccent.equals(accent.id()), () -> {
                        work.uiAccent = accent.id();
                        Theme.setAccent(accent);
                    }), 18);
        }

        buildAbout();
    }

    /** "About": name, version, author, license, link, the state of the translations, third-party credits. */
    private void buildAbout() {
        var meta = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(net.shattergui.ShatterGuiClient.MOD_ID)
                .map(net.fabricmc.loader.api.ModContainer::getMetadata).orElse(null);
        if (meta == null) {
            return;
        }
        Section about = section(tr("ui.shatter_gui.about.title"), null);
        about.add(new Label(Component.literal(meta.getName()), Label.Style.BODY));
        about.add(new Label(tr("ui.shatter_gui.about.version", meta.getVersion().getFriendlyString()), Label.Style.DIM));
        String authors = String.join(", ", meta.getAuthors().stream().map(net.fabricmc.loader.api.metadata.Person::getName).toList());
        about.add(new Label(tr("ui.shatter_gui.about.author", authors), Label.Style.DIM));
        about.add(new Label(tr("ui.shatter_gui.about.license", String.join(", ", meta.getLicense())), Label.Style.DIM));
        String link = meta.getContact().get("sources").orElse(null);
        if (link != null) {
            about.add(new Label(tr("ui.shatter_gui.about.link", link), Label.Style.DIM));
        }
        // The pages of the mod (fabric.mod.json "contact"), each opened through the game's own "open this link?" screen.
        List<Button> pages = new ArrayList<>();
        for (String[] page : new String[][]{{"modrinth", "modrinth"}, {"curseforge", "curseforge"}, {"sources", "github"},
                {"issues", "report_bug"}}) {
            String url = meta.getContact().get(page[0]).orElse(null);
            if (url == null) {
                continue;
            }
            String key = "ui.shatter_gui.about." + page[1];
            Button open = new Button(tr(key), page[0].equals("issues") ? Icon.INFO : Icon.GLOBE, Button.Kind.SECONDARY,
                    () -> net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, url)).leavesScreen(true);
            open.tooltip(tr(key), tr(key + ".tooltip"));
            pages.add(open);
        }
        // Cells as wide as the widest label, so no label is cut (they wrap to more rows instead).
        Grid links = about.add(new Grid(pages.stream().mapToInt(Button::naturalWidth).max().orElse(80), 6));
        pages.forEach(links::add);
        about.add(new Hint(tr("ui.shatter_gui.about.translations")));
        about.add(new Hint(tr("ui.shatter_gui.about.third_party")));
    }

    /** Thumbnail; next to it the chosen option's name and description, and the drop-down to change it. */
    private Row choiceRow(PreviewPlayer player, Supplier<Component> name, Supplier<Component> description, Widget dropdown) {
        Row row = new Row(8).wrapBelow(200);
        row.fixed(new Thumb(player, 0.62f, 60), 84);
        Column text = new Column(0, 3);
        text.add(new Label(name, Label.Style.BODY));
        text.add(new Label(description, Label.Style.DIM));
        Row picker = text.add(new Row(0).wrapBelow(170));
        picker.fixed(dropdown, 170);
        row.flex(text, 1);
        return row;
    }

    private void buildEffects() {
        Section top = section(tr("ui.shatter_gui.effects.title"), tr("ui.shatter_gui.effects.desc"));
        top.add(new Hint(tr("ui.shatter_gui.assign.hint")));
        Grid grid = new Grid(96, 6);
        content.add(grid);
        for (String id : effectIds()) {
            grid.add(effectCard(id, () -> selectedEffect.equals(id), () -> selectEffect(id)))
                    .onDoubleClick(() -> confirmBoth(id));
        }
        String id = selectedEffect;
        CustomEffectDef def = custom(id);
        Section settings = content.add(new Section("settings.effect_params", Icon.GEAR, () -> tr("ui.shatter_gui.effects.params_title", effectName(id)), false, 7));
        Row head = settings.add(new Row(6));
        head.flex(new Label(() -> effectDescription(id), Label.Style.DIM), 1);
        addAssignRow(settings, id);
        Button resetAll = new Button(tr("ui.shatter_gui.reset_all"), Icon.RESET, Button.Kind.GHOST, () -> {
            if (def != null) {
                def.resetParams();
            } else {
                work.resetGroup(id);
            }
        });
        resetAll.tooltip(tr("ui.shatter_gui.reset_all"), tr("ui.shatter_gui.reset_all.tooltip"));
        head.fixed(resetAll, resetAll.naturalWidth());
        if (def != null) {
            settings.add(new Hint(tr("ui.shatter_gui.effects.custom_note", ShatterEffects.displayName(def.base()))));
            addCustomParamRows(settings, def);
            return;
        }
        if (packDef(id) != null) {
            settings.add(new Hint(tr("ui.shatter_gui.effects.pack_note")));
            return;
        }
        DataEffect data = dataEffect(id);
        if (data != null && !data.bundled) {
            addDataInfo(settings, data);
            return;
        }
        List<Param<?>> params = Params.group(id);
        if (data != null) {
            settings.add(new Hint(tr(data.def.openable ? "ui.shatter_gui.effects.bundled_note.open" : "ui.shatter_gui.effects.bundled_note")));
            Button clone = new Button(tr("ui.shatter_gui.effects.clone"), Icon.COPY, Button.Kind.SECONDARY, () -> cloneBundled(id));
            clone.tooltip(tr("ui.shatter_gui.effects.clone"), tr("ui.shatter_gui.effects.clone.tooltip"));
            Row row = settings.add(new Row(6));
            row.fixed(clone, clone.naturalWidth());
        }
        if (params.isEmpty()) {
            settings.add(new Hint(tr("ui.shatter_gui.no_params")));
        }
        for (Param<?> param : params) {
            settings.add(paramRow(param, work));
        }
    }

    private Card effectCard(String id, BooleanSupplier selected, Runnable onClick) {
        Card card = new Card(5, 3).onClick(onClick).selected(selected);
        card.add(new Thumb(thumb("close-" + id, () -> closeScene(id, null, true, true)), 0.62f, 74));
        card.add(new Label(() -> effectName(id), Label.Style.BODY));
        card.add(new Label(() -> effectShort(id), Label.Style.SMALL));
        Row marks = card.add(new Row(3));
        marks.fixed(new FavoriteStar(id), FavoriteStar.SIZE);
        marks.flex(new Badges(id), 1);
        card.tooltip(effectName(id), effectDescription(id));
        return card;
    }

    private void buildScreens() {
        Section head = section(tr("ui.shatter_gui.screens.title"), tr("ui.shatter_gui.screens.desc"));
        Row tools = head.add(new Row(6).wrapBelow(240));
        tools.flex(search, 1);
        tools.fixed(new Dropdown<>(this::modOptions, () -> modFilter, v -> {
            modFilter = v;
            rebuildScreenList();
        }).tooltip(tr("ui.shatter_gui.screens.by_mod"), tr("ui.shatter_gui.screens.by_mod.tooltip")), 130);
        content.add(screenList);
        rebuildScreenList();
        buildThemes();
    }

    /** Thematic mode's rules: which effect each kind of interface closes with (when the mode is on in General). */
    private void buildThemes() {
        Section card = content.add(new Section("screens.themes", Icon.SPARKLES, () -> tr("ui.shatter_gui.themes.title"), false, 7));
        card.add(new Hint(() -> tr(work.thematic ? "ui.shatter_gui.themes.on" : "ui.shatter_gui.themes.off")));
        for (Themes.Rule rule : Themes.rules()) {
            Dropdown<String> pick = new Dropdown<>(() -> themeOptions(), () -> Themes.effectOf(rule, work.themes), v -> {
                if (v.equals(rule.effect())) {
                    work.themes.remove(rule.id());
                } else {
                    work.themes.put(rule.id(), v);
                }
            });
            pick.thumbs(26, 20);
            card.add(new SettingRow(tr("ui.shatter_gui.themes.rule." + rule.id()), tr("ui.shatter_gui.themes.rule." + rule.id() + ".desc"),
                    false, pick, () -> work.themes.containsKey(rule.id()), () -> work.themes.remove(rule.id())));
        }
        Button reset = new Button(tr("ui.shatter_gui.themes.reset"), Icon.RESET, Button.Kind.GHOST, () -> work.themes.clear());
        reset.tooltip(tr("ui.shatter_gui.themes.reset"), tr("ui.shatter_gui.themes.reset.tooltip"));
        Row row = card.add(new Row(6));
        row.fixed(reset, reset.naturalWidth());
    }

    private List<Dropdown.Option<String>> themeOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        options.add(new Dropdown.Option<>(Themes.GENERAL, tr("ui.shatter_gui.themes.general"), tr("ui.shatter_gui.themes.general.desc"),
                painter("close-" + known(work.effect), () -> closeScene(known(work.effect), null, true, true))));
        options.addAll(closeOptions());
        return options;
    }

    private void rebuildScreenList() {
        screenList.clear();
        if (tab != Tab.SCREENS) {
            return;
        }
        Map<String, ScreenCatalog.Entry> screens = allScreens();
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (ScreenCatalog.Entry entry : screens.values()) {
            if (!ALL_MODS.equals(modFilter) && !modFilter.equals(entry.modId())) {
                continue;
            }
            String title = screenTitle(entry);
            String mod = ScreenCatalog.modName(entry.modId());
            if (!query.isEmpty() && !(title.toLowerCase(Locale.ROOT).contains(query)
                    || entry.key().toLowerCase(Locale.ROOT).contains(query) || mod.toLowerCase(Locale.ROOT).contains(query))) {
                continue;
            }
            screenList.add(screenCard(entry, title, mod));
            shown++;
        }
        if (shown == 0) {
            Card empty = new Card(10, 4);
            empty.add(new Label(screens.isEmpty() ? tr("ui.shatter_gui.screens.none") : tr("ui.shatter_gui.screens.no_match"),
                    Label.Style.DIM));
            screenList.add(empty);
        }
    }

    private Card screenCard(ScreenCatalog.Entry entry, String title, String mod) {
        String key = entry.key();
        Card card = new Card(8, 6).onClick(() -> selectScreen(key)).selected(() -> key.equals(selectedScreen));
        Row header = card.add(new Row(8));
        header.fixed(new IconBox(key, entry.modId()), 18);
        Column names = new Column(0, 2);
        names.add(new Label(Component.literal(title), Label.Style.BODY));
        names.add(new Label(Component.literal(mod + "  ·  " + key), Label.Style.SMALL));
        header.flex(names, 1);
        card.tooltip(Component.literal(title), tr("ui.shatter_gui.screens.key", key, mod));
        Row pickers = card.add(new Row(8).wrapBelow(250));
        Column openCol = new Column(0, 2);
        openCol.add(new Label(tr("ui.shatter_gui.screens.on_open"), Label.Style.DIM));
        openCol.add(new Dropdown<>(this::screenOpenOptions, () -> readScreen(key)[0], v -> {
            writeScreen(key, v, readScreen(key)[1]);
            selectScreen(key);
        }).thumbs(26, 20)).tooltip(tr("ui.shatter_gui.screens.on_open"), tr("ui.shatter_gui.open.desc"));
        Column closeCol = new Column(0, 2);
        closeCol.add(new Label(tr("ui.shatter_gui.screens.on_close"), Label.Style.DIM));
        closeCol.add(new Dropdown<>(this::screenCloseOptions, () -> readScreen(key)[1], v -> {
            writeScreen(key, readScreen(key)[0], v);
            selectScreen(key);
        }).thumbs(26, 20)).tooltip(tr("ui.shatter_gui.screens.on_close"), tr("ui.shatter_gui.close.desc"));
        pickers.flex(openCol, 1);
        pickers.flex(closeCol, 1);
        return card;
    }

    /** A screen's icon (item, mod icon or a window). */
    private static final class IconBox extends Widget {
        private final String key;
        private final String modId;

        IconBox(String key, String modId) {
            this.key = key;
            this.modId = modId;
            this.h = 18;
        }

        @Override
        protected void draw(Ui ui) {
            Draw.roundRect(ui, x, y, 18, 18, 4, Theme.RAISED);
            ScreenIcons.draw(ui, key, modId, x + 1, y + 1, 16);
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    private void buildCustom() {
        if (!classicCustom) {
            // The effect editor fills the tab (see layoutEditor).
            return;
        }
        Section back = section(tr("ui.shatter_gui.fx.classic.title"), tr("ui.shatter_gui.fx.classic.desc"));
        Row backRow = back.add(new Row(6));
        Button openEditor = new Button(tr("ui.shatter_gui.fx.classic.open_editor"), Icon.SPARKLES, Button.Kind.PRIMARY,
                () -> setClassicCustom(false));
        openEditor.tooltip(tr("ui.shatter_gui.fx.classic.open_editor"), tr("ui.shatter_gui.fx.help.button.desc"));
        backRow.fixed(openEditor, openEditor.naturalWidth());
        Section head = section(tr("ui.shatter_gui.custom.title"), tr("ui.shatter_gui.custom.desc"));
        Row tools = head.add(new Row(6).wrapBelow(250));
        tools.flex(new Dropdown<>(this::closeOptions, () -> known(cloneFrom), v -> cloneFrom = v).thumbs(26, 20)
                .tooltip(tr("ui.shatter_gui.custom.create"), tr("ui.shatter_gui.custom.create.tooltip")), 1);
        Button create = new Button(tr("ui.shatter_gui.custom.create"), Icon.PLUS, Button.Kind.PRIMARY, () -> createCustom(cloneFrom));
        create.tooltip(tr("ui.shatter_gui.custom.create"), tr("ui.shatter_gui.custom.create.tooltip"));
        tools.fixed(create, create.naturalWidth());
        Button importButton = new Button(tr("ui.shatter_gui.custom.import"), Icon.IMPORT, Button.Kind.SECONDARY,
                () -> importClipboard());
        importButton.tooltip(tr("ui.shatter_gui.custom.import"), tr("ui.shatter_gui.custom.import.tooltip"));
        tools.fixed(importButton, importButton.naturalWidth());
        Button reload = new Button(tr("ui.shatter_gui.custom.reload"), Icon.REPLAY, Button.Kind.SECONDARY, this::reloadEffects);
        reload.tooltip(tr("ui.shatter_gui.custom.reload"), tr("ui.shatter_gui.custom.reload.tooltip"));
        tools.fixed(reload, reload.naturalWidth());

        buildDataSection();
        if (customs.isEmpty()) {
            Card empty = new Card(10, 4);
            empty.add(new Label(tr("ui.shatter_gui.custom.none"), Label.Style.DIM));
            content.add(empty);
            return;
        }
        Grid grid = new Grid(96, 6);
        content.add(grid);
        for (CustomEffectDef def : customs) {
            String id = def.id();
            grid.add(effectCard(id, () -> id.equals(selectedCustom), () -> selectCustom(id)));
        }
        CustomEffectDef def = custom(selectedCustom);
        if (def == null) {
            return;
        }
        Section details = content.add(new Section("settings.custom_details", Icon.STAR, () -> Component.literal(def.name()), false, 7));
        Row nameRow = details.add(new Row(6).wrapBelow(250));
        TextField name = new TextField(CustomEffectDef.MAX_NAME).hint(tr("ui.shatter_gui.custom.name")).onChange(def::rename);
        name.value(def.name());
        name.tooltip(tr("ui.shatter_gui.custom.name"), tr("ui.shatter_gui.custom.name.tooltip", def.id()));
        nameRow.flex(name, 1);
        Button export = new Button(tr("ui.shatter_gui.custom.export"), Icon.EXPORT, Button.Kind.SECONDARY, () -> exportSelected());
        export.tooltip(tr("ui.shatter_gui.custom.export"), tr("ui.shatter_gui.custom.export.tooltip"));
        nameRow.fixed(export, export.naturalWidth());
        Button delete = new Button(tr("ui.shatter_gui.custom.delete"), Icon.TRASH, Button.Kind.DANGER,
                () -> ConfirmDialog.open(tr("ui.shatter_gui.custom.delete.confirm_title"),
                        tr("ui.shatter_gui.custom.delete.confirm", def.name()), tr("ui.shatter_gui.custom.delete"), true,
                        this::deleteSelected));
        nameRow.fixed(delete, delete.naturalWidth());
        details.add(new Label(() -> tr("ui.shatter_gui.custom.info", ShatterEffects.displayName(def.base()), def.id(),
                def.particles().size(), def.sounds().size()), Label.Style.DIM));
        Row resetRow = details.add(new Row(6));
        resetRow.flex(new Label(tr("ui.shatter_gui.custom.params"), Label.Style.BODY), 1);
        Button resetAll = new Button(tr("ui.shatter_gui.reset_all"), Icon.RESET, Button.Kind.GHOST, def::resetParams);
        resetAll.tooltip(tr("ui.shatter_gui.reset_all"), tr("ui.shatter_gui.custom.reset.tooltip"));
        resetRow.fixed(resetAll, resetAll.naturalWidth());
        addCustomParamRows(details, def);
        details.add(new Hint(tr("ui.shatter_gui.custom.later")));
    }

    // --- parameter rows ---

    /** A row for a registry parameter: slider/toggle/list, units, help, and back-to-default. */
    private SettingRow paramRow(Param<?> param, ParamStore store) {
        return new SettingRow(tr(param.langKey()), help(param), false, control(param, store),
                () -> !Objects.equals(store.get(param), param.def()), () -> store.set(param, param.def()));
    }

    private Widget control(Param<?> param, ParamStore store) {
        return switch (param) {
            case Param.Float p -> new Slider(p.min(), p.max(), ParamUi.step(p), () -> store.get(p), v -> store.set(p, v),
                    v -> ParamUi.format(p, v), text -> ParamUi.parse(p, text));
            case Param.Int p -> new Slider(p.min(), p.max(), ParamUi.step(p), () -> store.get(p), v -> store.set(p, v),
                    v -> ParamUi.format(p, v), text -> ParamUi.parse(p, text));
            case Param.Bool p -> new Toggle(() -> store.get(p), v -> store.set(p, v));
            case Param.Choice p -> new Dropdown<>(() -> {
                List<Dropdown.Option<String>> options = new ArrayList<>();
                for (String choice : p.choices()) {
                    options.add(new Dropdown.Option<>(choice, tr(p.choiceKey(choice))));
                }
                return options;
            }, () -> store.get(p), v -> store.set(p, v));
        };
    }

    /** What it does, plus range and default in the shown units. */
    private static Component help(Param<?> param) {
        Component range = switch (param) {
            case Param.Float p -> tr("ui.shatter_gui.range", ParamUi.format(p, p.min()), ParamUi.format(p, p.max()),
                    ParamUi.format(p, p.def()));
            case Param.Int p -> tr("ui.shatter_gui.range", ParamUi.format(p, p.min()), ParamUi.format(p, p.max()),
                    ParamUi.format(p, p.def()));
            case Param.Bool p -> tr("ui.shatter_gui.default", tr(p.def() ? "options.on" : "options.off"));
            case Param.Choice p -> tr("ui.shatter_gui.default", tr(p.choiceKey(p.def())));
        };
        return Component.translatable(param.langKey() + ".tooltip").copy().append("\n").append(range);
    }

    /**
     * The base effect's parameters for a custom effect: one it does not set shows (and uses) the player's value for
     * the base effect; changing it makes it the custom effect's own; reset drops it again.
     */
    private void addCustomParamRows(net.shattergui.ui.Container card, CustomEffectDef def) {
        ParamStore layered = new ParamStore() {
            @Override
            public <T> T get(Param<T> param) {
                return def.has(param) ? def.get(param) : work.get(param);
            }

            @Override
            public <T> T set(Param<T> param, Object raw) {
                return def.set(param, raw);
            }
        };
        for (Param<?> param : Params.group(def.base())) {
            card.add(new SettingRow(tr(param.langKey()), help(param), false, control(param, layered),
                    () -> def.has(param), () -> def.remove(param)));
        }
    }

    // --- choosing effects on the Effects tab ---

    /** The open effect that plays {@code id} (fly: out of the block), or null if it has no opening. */
    private static String openValue(String id) {
        ShatterEffect effect = ShatterEffects.byName(id);
        return ShatterConfig.openValueFor(id, ShatterEffects.canOpen(id),
                effect != null && effect.base() instanceof net.shattergui.effect.FlyEffect);
    }

    private boolean usedForOpen(String id) {
        String open = openValue(id);
        return open != null && open.equals(work.openEffect);
    }

    private boolean usedForClose(String id) {
        return known(work.effect).equals(id);
    }

    private void setPreviewMode(PreviewMode mode) {
        previewMode = mode;
        live.restart();
    }

    private void useForOpen(String id) {
        String open = openValue(id);
        if (open != null) {
            work.openEffect = open;
            setStatus(tr("ui.shatter_gui.assign.done.open", effectName(id)), true);
        }
    }

    private void useForClose(String id) {
        work.effect = id;
        setStatus(tr("ui.shatter_gui.assign.done.close", effectName(id)), true);
    }

    /** No longer opening (just appears) nor closing (back to glass) with {@code id}. */
    private void stopUsing(String id) {
        if (usedForOpen(id)) {
            work.openEffect = ShatterConfig.OPEN_NONE;
        }
        if (usedForClose(id) && !GlassEffect.ID.equals(id)) {
            work.effect = GlassEffect.ID;
        }
        setStatus(tr("ui.shatter_gui.assign.done.removed", effectName(id)), true);
    }

    /** Double click on a card: after a question, the effect opens and closes interfaces (or only closes them). */
    private void confirmBoth(String id) {
        boolean opens = openValue(id) != null;
        ConfirmDialog.open(tr("ui.shatter_gui.assign.confirm.title"),
                tr(opens ? "ui.shatter_gui.assign.confirm.both" : "ui.shatter_gui.assign.confirm.close_only", effectName(id)),
                tr("ui.shatter_gui.assign.confirm.ok"), false, () -> {
                    useForClose(id);
                    if (opens) {
                        useForOpen(id);
                        setStatus(tr("ui.shatter_gui.assign.done.both", effectName(id)), true);
                    }
                });
    }

    private void toggleFavorite(String id) {
        if (!work.favorites.remove(id)) {
            work.favorites.add(id);
        }
    }

    /** "Use for opening / closing / both", "Remove": under the selected effect's name. */
    private void addAssignRow(net.shattergui.ui.Container card, String id) {
        boolean opens = openValue(id) != null;
        Button open = new Button(tr("ui.shatter_gui.assign.open"), null, Button.Kind.SECONDARY, () -> useForOpen(id)) {
            @Override
            protected void draw(Ui ui) {
                selected(usedForOpen(id));
                super.draw(ui);
            }
        };
        open.enabled = opens;
        open.tooltip(tr("ui.shatter_gui.assign.open"), tr(opens ? "ui.shatter_gui.assign.open.tooltip"
                : "ui.shatter_gui.assign.open.none.tooltip"));
        Button close = new Button(tr("ui.shatter_gui.assign.close"), null, Button.Kind.SECONDARY, () -> useForClose(id));
        close.tooltip(tr("ui.shatter_gui.assign.close"), tr("ui.shatter_gui.assign.close.tooltip"));
        Button both = new Button(tr("ui.shatter_gui.assign.both"), null, Button.Kind.PRIMARY, () -> {
            useForClose(id);
            useForOpen(id);
            setStatus(tr("ui.shatter_gui.assign.done.both", effectName(id)), true);
        });
        both.enabled = opens;
        both.tooltip(tr("ui.shatter_gui.assign.both"), tr(opens ? "ui.shatter_gui.assign.both.tooltip"
                : "ui.shatter_gui.assign.open.none.tooltip"));
        Button remove = new Button(tr("ui.shatter_gui.assign.remove"), Icon.TRASH, Button.Kind.GHOST, () -> stopUsing(id)) {
            @Override
            protected void draw(Ui ui) {
                enabled = usedForOpen(id) || usedForClose(id) && !GlassEffect.ID.equals(id);
                super.draw(ui);
            }
        };
        remove.tooltip(tr("ui.shatter_gui.assign.remove"), tr("ui.shatter_gui.assign.remove.tooltip"));
        // Cells as wide as the widest label: a narrow card (long translations, large GUI scale) wraps to two rows
        // instead of cutting the labels.
        List<Button> buttons = List.of(open, close, both, remove);
        Grid row = card.add(new Grid(buttons.stream().mapToInt(Button::naturalWidth).max().orElse(60), 4));
        buttons.forEach(row::add);
    }

    /** The star on an effect card: marks a favourite (the cycle key switches between favourites). */
    private final class FavoriteStar extends Widget {
        static final int SIZE = 11;
        private final String id;

        FavoriteStar(String id) {
            this.id = id;
            tooltip(tr("ui.shatter_gui.favorite"), tr("ui.shatter_gui.favorite.tooltip"));
        }

        @Override
        protected int measure(int width) {
            return SIZE;
        }

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        protected void draw(Ui ui) {
            boolean on = work.favorites.contains(id);
            boolean over = ui.isHovered(this);
            int color = on ? Theme.WARNING : over ? Theme.TEXT : Theme.TEXT_FAINT;
            if (over || focus.value() > 0.05f) {
                Draw.roundRect(ui, x, y, w, h, 3, Theme.RAISED_HOVER);
            }
            Draw.iconCentered(ui, Icon.STAR, x, y, w, h, color);
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            click();
            toggleFavorite(id);
            return true;
        }

        @Override
        public boolean doubleClicked(double mx, double my) {
            // The second click of a quick double click on the star is a second toggle, not "use for both".
            return mouseDown(mx, my, 0);
        }

        @Override
        public boolean key(net.minecraft.client.input.KeyEvent event) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
                    || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
                click();
                toggleFavorite(id);
                return true;
            }
            return false;
        }
    }

    /** "Opening" / "Closing" marks on an effect card: what the effect is used for now. */
    private final class Badges extends Widget {
        private final String id;

        Badges(String id) {
            this.id = id;
        }

        @Override
        protected int measure(int width) {
            return FavoriteStar.SIZE;
        }

        @Override
        public Component tooltipTitle() {
            return usedForOpen(id) || usedForClose(id) ? tr("ui.shatter_gui.badge.title") : null;
        }

        @Override
        public Component tooltipText() {
            return usedForOpen(id) || usedForClose(id) ? tr("ui.shatter_gui.badge.tooltip") : null;
        }

        @Override
        protected void draw(Ui ui) {
            int bx = x;
            if (usedForOpen(id)) {
                bx = badge(ui, bx, tr("ui.shatter_gui.badge.open").getString(), Theme.SUCCESS);
            }
            if (usedForClose(id)) {
                badge(ui, bx, tr("ui.shatter_gui.badge.close").getString(), Theme.accent());
            }
        }

        private int badge(Ui ui, int bx, String text, int color) {
            int room = x + w - bx;
            if (room < 12) {
                return bx;
            }
            int tw = Math.min(room - 6, Math.round(ui.font.width(text) * Ui.smallScale()) + 1);
            Draw.roundRect(ui, bx, y, tw + 6, h, 3, Theme.fade(color, 0.35f));
            ui.textSmall(text, bx + 3, y + 2, tw, Theme.TEXT);
            return bx + tw + 9;
        }
    }

    // --- options ---

    private List<Dropdown.Option<String>> openOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        List<String> ids = new ArrayList<>(OPEN_IDS);
        // Only effects that really play backwards (fly opens by flying out of the block, above).
        for (String id : ShatterEffects.openIds()) {
            ids.add(ShatterConfig.OPEN_DATA_PREFIX + id);
        }
        for (String id : ids) {
            options.add(new Dropdown.Option<>(id, openName(id), openDescription(id), painter("open-" + id, () -> openScene(id))));
        }
        return options;
    }

    /** Effects whose backwards run is something else than gathering, with a name of its own ("from embers"). */
    private static final java.util.Set<String> OWN_OPEN_NAMES = java.util.Set.of(BurnEffect.ID);

    /** Name of an open effect: "none", "fly_from_block" or "open:<effect>" (that effect, gathering). */
    private Component openName(String open) {
        String id = ShatterConfig.openDataId(open);
        if (id != null && OWN_OPEN_NAMES.contains(id)) {
            return tr("ui.shatter_gui.open.effect." + id);
        }
        return id != null ? tr("ui.shatter_gui.open.data", effectName(id)) : tr("ui.shatter_gui.open." + open);
    }

    private Component openDescription(String open) {
        String id = ShatterConfig.openDataId(open);
        if (id != null) {
            if (!ShatterEffects.canOpen(id)) {
                return tr("ui.shatter_gui.open.data.missing");
            }
            return OWN_OPEN_NAMES.contains(id) ? tr("ui.shatter_gui.open.effect." + id + ".desc")
                    : tr("ui.shatter_gui.open.data.desc", effectName(id));
        }
        return tr("ui.shatter_gui.open." + open + ".desc");
    }

    /** Opens a copy of a shipped effect in the effect editor (Custom tab), unsaved, under a free id. */
    public void cloneBundled(String id) {
        String json = FxEffects.bundledJson(id);
        if (json == null) {
            return;
        }
        selectTab(Tab.CUSTOM);
        setClassicCustom(false);
        fullscreenIfSmall = true;
        effectEditor().confirmLeave(() -> effectEditor().cloneFrom(json, effectName(id).getString()));
    }

    private List<Dropdown.Option<String>> closeOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        for (String id : effectIds()) {
            options.add(new Dropdown.Option<>(id, effectName(id), effectDescription(id),
                    painter("close-" + id, () -> closeScene(id, null, true, true))));
        }
        return options;
    }

    private List<Dropdown.Option<String>> screenOpenOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        options.add(new Dropdown.Option<>(DEFAULT, tr("ui.shatter_gui.screens.as_general"), tr("ui.shatter_gui.screens.as_general.desc"),
                painter("open-" + work.openEffect, () -> openScene(work.openEffect))));
        options.addAll(openOptions());
        return options;
    }

    private List<Dropdown.Option<String>> screenCloseOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        options.add(new Dropdown.Option<>(DEFAULT, tr("ui.shatter_gui.screens.as_general"), tr("ui.shatter_gui.screens.as_general.desc"),
                painter("close-" + known(work.effect), () -> closeScene(known(work.effect), null, true, true))));
        options.addAll(closeOptions());
        options.add(new Dropdown.Option<>(OFF, tr("ui.shatter_gui.screens.off"), tr("ui.shatter_gui.screens.off.desc"), null));
        return options;
    }

    private List<Dropdown.Option<String>> modOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        options.add(new Dropdown.Option<>(ALL_MODS, tr("ui.shatter_gui.screens.all_mods")));
        Set<String> mods = new LinkedHashSet<>();
        for (ScreenCatalog.Entry entry : allScreens().values()) {
            mods.add(entry.modId());
        }
        for (String mod : mods) {
            options.add(new Dropdown.Option<>(mod, Component.literal(ScreenCatalog.modName(mod))));
        }
        return options;
    }

    // --- scenes and thumbnails ---

    private PreviewPlayer thumb(String key, Supplier<Scene> scene) {
        return thumbs.computeIfAbsent(key, k -> new PreviewPlayer(scene, false, true));
    }

    private Dropdown.Painter painter(String key, Supplier<Scene> scene) {
        PreviewPlayer player = thumb(key, scene);
        return player::render;
    }

    private Scene openScene(String openEffect) {
        return new Scene(openEffect, null, frameValues, null, true, true);
    }

    /** The close effect {@code id} as a scene (a custom effect: its base, values and particles). */
    private Scene closeScene(String id, String open, boolean hasSource, boolean edgeAllowed) {
        DataEffect data = dataEffect(id);
        if (data != null) {
            return Scene.data(open, data, frameValues, hasSource, edgeAllowed);
        }
        CustomEffectDef def = custom(id);
        if (def == null) {
            def = packDef(id);
        }
        if (def != null) {
            return new Scene(open, def.base(), frameValues.with(def.params()), def, hasSource, edgeAllowed);
        }
        return new Scene(open, ShatterEffects.isBuiltIn(id) ? id : GlassEffect.ID, frameValues, null, hasSource, edgeAllowed);
    }

    /** What the big preview plays: depends on the tab and the selection there. */
    private Scene liveScene() {
        switch (tab) {
            case EFFECTS -> {
                String open = openValue(selectedEffect);
                return switch (previewMode) {
                    case OPEN -> new Scene(open, null, frameValues, null, true, true);
                    case CLOSE -> closeScene(selectedEffect, null, true, true);
                    case BOTH -> closeScene(selectedEffect, open, true, true);
                };
            }
            case CUSTOM -> {
                if (selectedCustom != null && custom(selectedCustom) != null) {
                    return closeScene(selectedCustom, work.openEffect, true, true);
                }
                return closeScene(known(work.effect), work.openEffect, true, true);
            }
            case SCREENS -> {
                if (selectedScreen == null) {
                    return closeScene(known(work.effect), work.openEffect, true, true);
                }
                String[] chosen = readScreen(selectedScreen);
                String open = DEFAULT.equals(chosen[0]) ? work.openEffect : chosen[0];
                boolean container = selectedScreen.contains(":") && !ScreenRules.INVENTORY_KEY.equals(selectedScreen);
                String simple = selectedScreen.substring(selectedScreen.lastIndexOf('.') + 1);
                boolean edge = !NO_EDGE.contains(simple);
                if (OFF.equals(chosen[1])) {
                    return new Scene(open, null, frameValues, null, container, edge);
                }
                String close = DEFAULT.equals(chosen[1]) ? known(work.effect) : known(chosen[1]);
                return closeScene(close, open, container, edge);
            }
            default -> {
                return closeScene(known(work.effect), work.openEffect, true, true);
            }
        }
    }

    private Component previewCaption() {
        return switch (tab) {
            case EFFECTS -> tr("ui.shatter_gui.preview.effect", effectName(selectedEffect));
            case CUSTOM -> selectedCustom != null ? tr("ui.shatter_gui.preview.effect", effectName(selectedCustom))
                    : tr("ui.shatter_gui.preview.general");
            case SCREENS -> {
                if (selectedScreen == null) {
                    yield tr("ui.shatter_gui.preview.pick_screen");
                }
                ScreenCatalog.Entry entry = allScreens().get(selectedScreen);
                yield tr("ui.shatter_gui.preview.screen", screenTitle(entry != null ? entry
                        : new ScreenCatalog.Entry(selectedScreen, "", "?", "")));
            }
            default -> tr("ui.shatter_gui.preview.general");
        };
    }

    // --- screens data ---

    /** Seen screens (with their titles) plus every screen that has a setting. */
    private Map<String, ScreenCatalog.Entry> allScreens() {
        Map<String, ScreenCatalog.Entry> screens = new LinkedHashMap<>();
        for (ScreenCatalog.Entry entry : ScreenCatalog.entries()) {
            screens.put(entry.key(), entry);
        }
        for (String key : ScreenRules.seen().keySet()) {
            screens.putIfAbsent(key, new ScreenCatalog.Entry(key, "", ScreenCatalog.ownerOfKey(key), key));
        }
        for (String key : work.overrides.keySet()) {
            screens.putIfAbsent(key, new ScreenCatalog.Entry(key, "", ScreenCatalog.ownerOfKey(key), key));
        }
        for (String key : work.disabledScreens) {
            screens.putIfAbsent(key, new ScreenCatalog.Entry(key, "", ScreenCatalog.ownerOfKey(key), key));
        }
        return screens;
    }

    /**
     * The remembered title, else a readable name from the key ("generic_9x3" → "Generic 9x3", "PauseScreen" → "Pause").
     * The player's inventory is always "Inventory": its screen is titled "Crafting" in vanilla.
     */
    private static String screenTitle(ScreenCatalog.Entry entry) {
        if (ScreenRules.INVENTORY_KEY.equals(entry.key())) {
            return Component.translatable("container.inventory").getString();
        }
        if (entry.title() != null && !entry.title().isBlank()) {
            return entry.title();
        }
        String key = entry.key();
        String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key.substring(key.lastIndexOf('.') + 1);
        name = name.replace("Screen", "").replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return name.isEmpty() ? key : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** {open, close} for a screen key: "default", an effect name, or "off" (close: no effect). */
    private String[] readScreen(String key) {
        String open = DEFAULT;
        String close = DEFAULT;
        String value = work.overrides.get(key);
        if (value != null) {
            for (String part : value.split(",")) {
                String name = part.trim();
                if (ShatterConfig.isOpenEffect(name)) {
                    open = name;
                } else if (!name.isEmpty()) {
                    close = name;
                }
            }
        }
        if (work.disabledScreens.contains(key)) {
            close = OFF;
        }
        return new String[]{open, close};
    }

    private void writeScreen(String key, String open, String close) {
        work.overrides.remove(key);
        work.disabledScreens.remove(key);
        if (OFF.equals(close)) {
            work.disabledScreens.add(key);
        }
        List<String> parts = new ArrayList<>(2);
        if (!DEFAULT.equals(open)) {
            parts.add(open);
        }
        if (!DEFAULT.equals(close) && !OFF.equals(close)) {
            parts.add(close);
        }
        if (!parts.isEmpty()) {
            work.overrides.put(key, String.join(",", parts));
        }
    }

    // --- effects data ---

    /** Built-in effects, the player's custom effects being edited, then those only in resource packs. */
    private List<String> effectIds() {
        List<String> ids = new ArrayList<>(ShatterEffects.builtInIds());
        for (CustomEffectDef def : customs) {
            ids.add(def.id());
        }
        for (CustomEffectDef def : CustomEffects.fromPacks()) {
            if (!ids.contains(def.id())) {
                ids.add(def.id());
            }
        }
        for (String id : ShatterEffects.dataIds()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** A loaded schema-2 effect, or null. */
    private static DataEffect dataEffect(String id) {
        return ShatterEffects.data(id) instanceof DataEffect data ? data : null;
    }

    /** {@code id} if the editor knows that effect, else glass. */
    private String known(String id) {
        return effectIds().contains(id) ? id : GlassEffect.ID;
    }

    private Component effectName(String id) {
        CustomEffectDef def = custom(id);
        return def != null ? Component.literal(def.name()) : ShatterEffects.displayName(id);
    }

    private Component effectDescription(String id) {
        DataEffect data = dataEffect(id);
        if (data != null && !data.bundled) {
            return data.def.description.isEmpty() ? tr("ui.shatter_gui.effect.data.desc") : Component.literal(data.def.description);
        }
        CustomEffectDef def = custom(id);
        if (def == null) {
            def = packDef(id);
        }
        if (def != null) {
            return tr("ui.shatter_gui.effect.custom.desc", ShatterEffects.displayName(def.base()));
        }
        return tr("ui.shatter_gui.effect." + id + ".desc");
    }

    private Component effectShort(String id) {
        if (dataEffect(id) != null && !dataEffect(id).bundled) {
            return tr("ui.shatter_gui.effect.data.short");
        }
        CustomEffectDef def = custom(id);
        if (def == null) {
            def = packDef(id);
        }
        if (def != null) {
            return tr("ui.shatter_gui.effect.custom.short", ShatterEffects.displayName(def.base()));
        }
        return tr("ui.shatter_gui.effect." + id + ".short");
    }

    private CustomEffectDef custom(String id) {
        for (CustomEffectDef def : customs) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    private static CustomEffectDef packDef(String id) {
        for (CustomEffectDef def : CustomEffects.fromPacks()) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /** The effect for the in-world preview: a custom one as edited (unsaved), else the registered one. */
    private ShatterEffect effect(String id) {
        CustomEffectDef def = custom(id);
        if (def != null) {
            return new CustomEffect(def, ShatterEffects.builtIn(def.base()));
        }
        return ShatterEffects.resolve(id);
    }

    // --- custom effect actions ---

    /** A new custom effect copied from {@code fromId}: a custom one as it is, a built-in one with the current values. */
    public CustomEffectDef createCustom(String fromId) {
        String from = known(fromId);
        CustomEffectDef source = custom(from);
        if (source == null) {
            source = packDef(from);
        }
        CustomEffectDef created;
        if (source != null) {
            created = source.copy(freeId(source.id()), tr("ui.shatter_gui.custom.copy_of", source.name()).getString());
        } else {
            created = new CustomEffectDef(freeId(from), tr("ui.shatter_gui.custom.copy_of", ShatterEffects.displayName(from)).getString(), from);
            for (Param<?> param : Params.group(from)) {
                Object value = work.get(param);
                if (!value.equals(param.def())) {
                    created.set(param, value);
                }
            }
        }
        created.rename(created.name());
        customs.add(created);
        selectedCustom = created.id();
        setStatus(tr("ui.shatter_gui.custom.created", created.name()), true);
        rebuild();
        return created;
    }

    public void deleteSelected() {
        CustomEffectDef def = custom(selectedCustom);
        if (def == null) {
            return;
        }
        customs.remove(def);
        if (def.id().equals(work.effect)) {
            work.effect = GlassEffect.ID;
        }
        if (def.id().equals(selectedEffect)) {
            selectedEffect = known(work.effect);
        }
        selectedCustom = customs.isEmpty() ? null : customs.getFirst().id();
        setStatus(tr("ui.shatter_gui.custom.deleted", def.name()), true);
        rebuild();
    }

    /** Copies the selected effect's code (compressed JSON) to the clipboard; returns it. */
    public String exportSelected() {
        CustomEffectDef def = custom(selectedCustom);
        if (def == null) {
            return null;
        }
        String code = def.toCode();
        minecraft.keyboardHandler.setClipboard(code);
        setStatus(tr("ui.shatter_gui.custom.exported", def.name(), code.length()), true);
        return code;
    }

    /** Reads an effect code from the clipboard, validates it and adds the effect (under a free id if taken). */
    public CustomEffectDef importClipboard() {
        String clipboard = minecraft.keyboardHandler.getClipboard();
        String json = FxEffects.fromCode(clipboard);
        if (json != null && FxParser.schemaOf(json) == FxLimits.SCHEMA) {
            // A schema-2 effect: checked, saved as effects/<id>.json and loaded.
            FxProblems problems = FxEffects.importJson(json);
            if (problems.hasErrors()) {
                setStatus(tr("ui.shatter_gui.custom.import_failed", problems.errorSummary()), false);
            } else {
                setStatus(tr("ui.shatter_gui.custom.imported_data"), true);
                rebuild();
            }
            return null;
        }
        try {
            CustomEffectDef read = CustomEffectDef.fromCode(clipboard);
            CustomEffectDef added = read.copy(freeId(read.id()), read.name());
            customs.add(added);
            selectedCustom = added.id();
            setStatus(tr("ui.shatter_gui.custom.imported", added.name()), true);
            rebuild();
            return added;
        } catch (CustomEffectDef.InvalidEffectException e) {
            setStatus(tr("ui.shatter_gui.custom.import_failed", e.getMessage()), false);
            return null;
        }
    }

    /** Reads custom effects (both formats), packages and their textures again from disk and resource packs. */
    public void reloadEffects() {
        CustomEffects.reloadConfig();
        FxEffects.reloadAll();
        thumbs.clear();
        long errors = FxEffects.messages().stream().filter(m -> m.startsWith("error")).count();
        setStatus(tr("ui.shatter_gui.custom.reloaded", ShatterEffects.dataIds().size(), errors), errors == 0);
        rebuild();
    }

    /** Copies a schema-2 effect's code (only effects without textures; others are shared as a package). */
    public String exportData(String id) {
        DataEffect data = dataEffect(id);
        String code = data == null ? null : FxEffects.toCode(data);
        if (code == null) {
            setStatus(tr("ui.shatter_gui.custom.export_package"), false);
            return null;
        }
        minecraft.keyboardHandler.setClipboard(code);
        setStatus(tr("ui.shatter_gui.custom.exported", data.def.name, code.length()), true);
        return code;
    }

    /** The schema-2 effects, with where they come from, and the messages of the last load. */
    private void buildDataSection() {
        Section card = section(tr("ui.shatter_gui.data.title"), tr("ui.shatter_gui.data.desc"));
        List<String> ids = new ArrayList<>(ShatterEffects.dataIds());
        ids.removeIf(ShatterEffects::isBundled);
        if (ids.isEmpty()) {
            card.add(new Label(tr("ui.shatter_gui.data.none"), Label.Style.DIM));
        } else {
            Grid grid = card.add(new Grid(96, 6));
            for (String id : ids) {
                grid.add(effectCard(id, () -> id.equals(selectedEffect), () -> {
                    selectedEffect = id;
                    selectTab(Tab.EFFECTS);
                }));
            }
        }
        List<String> messages = FxEffects.messages();
        if (!messages.isEmpty()) {
            card.add(new Label(tr("ui.shatter_gui.data.messages", messages.size()), Label.Style.BODY));
            for (String message : messages.subList(0, Math.min(12, messages.size()))) {
                card.add(new Label(Component.literal("• " + message), Label.Style.DIM));
            }
        }
    }

    /** What a schema-2 effect is made of (it is edited as a file; the editor shows and previews it). */
    private void addDataInfo(net.shattergui.ui.Container card, DataEffect data) {
        card.add(new Label(tr("ui.shatter_gui.data.info", data.def.fracture.mode(), data.def.trigger.type(),
                data.def.layers.size(), data.def.emitters.size(), data.def.duration / 20f), Label.Style.DIM));
        card.add(new Label(tr("ui.shatter_gui.data.origin", data.origin), Label.Style.SMALL));
        if (!data.def.textures.isEmpty()) {
            card.add(new Label(tr("ui.shatter_gui.data.textures", String.join(", ", data.loadedTextures()),
                    data.def.textures.size()), Label.Style.DIM));
        }
        card.add(new Hint(tr("ui.shatter_gui.data.edit_note")));
        if (data.def.textures.isEmpty()) {
            Button export = new Button(tr("ui.shatter_gui.custom.export"), Icon.EXPORT, Button.Kind.SECONDARY,
                    () -> exportData(data.def.id));
            export.tooltip(tr("ui.shatter_gui.custom.export"), tr("ui.shatter_gui.custom.export.tooltip"));
            Row row = card.add(new Row(6));
            row.fixed(export, export.naturalWidth());
        }
    }

    /** {@code wanted} (made a valid id) if free, else with _2, _3, ... */
    private String freeId(String wanted) {
        String base = wanted.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isEmpty()) {
            base = "custom";
        }
        base = base.length() > 28 ? base.substring(0, 28) : base;
        String id = base;
        for (int n = 2; ShatterEffects.isReserved(id) || custom(id) != null || packDef(id) != null
                || OPEN_IDS.contains(id) || DEFAULT.equals(id) || OFF.equals(id); n++) {
            id = base + "_" + n;
        }
        return id;
    }

    private void setStatus(Component text, boolean ok) {
        status = text;
        statusColor = ok ? Theme.SUCCESS : Theme.DANGER;
        statusTime = UiClock.time();
    }

    // --- actions ---

    /** Plays the chosen effect in the world with the unsaved settings on a snapshot of this screen, then comes back. */
    public void preview() {
        String id = switch (tab) {
            case CUSTOM -> selectedCustom != null ? selectedCustom : selectedEffect;
            case EFFECTS -> selectedEffect;
            default -> known(work.effect);
        };
        if (!PreviewRunner.start(this, effect(id), work.values())) {
            setStatus(tr("ui.shatter_gui.preview.no_world"), false);
        }
    }

    private boolean computeDirty() {
        if (!work.toJsonString().equals(startJson)) {
            return true;
        }
        if (fxEditor != null && fxEditor.doc.dirty()) {
            return true;
        }
        List<String> now = customs.stream().map(CustomEffectDef::toJsonString).toList();
        return !now.equals(customsAtStart);
    }

    public void save() {
        if (fxEditor != null && fxEditor.doc.dirty()) {
            fxEditor.save();
        }
        disposeEditor();
        List<String> now = customs.stream().map(CustomEffectDef::toJsonString).toList();
        if (!now.equals(customsAtStart)) {
            CustomEffects.saveAll(customs);
        }
        boolean wasEnabled = ShatterConfig.get().enabled;
        ShatterConfig.replace(work.copy());
        Theme.setAccent(Theme.Accent.byId(work.uiAccent));
        if (wasEnabled && !work.enabled) {
            ShatterKeys.setEnabled(false);
        }
        minecraft.setScreen(parent);
    }

    /** Esc and Cancel: asks first if something changed. */
    @Override
    protected void escape() {
        if (computeDirty()) {
            ConfirmDialog.open(tr("ui.shatter_gui.discard.title"), tr("ui.shatter_gui.discard.message"),
                    tr("ui.shatter_gui.discard.confirm"), true, this::onClose);
        } else {
            onClose();
        }
    }

    /** Closes without saving. */
    @Override
    public void onClose() {
        disposeEditor();
        RealPreview.setConfigured(ShatterConfig.get().realPreview);
        Theme.setAccent(Theme.Accent.byId(savedAccent));
        minecraft.setScreen(parent);
    }
}
