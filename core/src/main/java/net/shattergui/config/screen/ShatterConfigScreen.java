package net.shattergui.config.screen;

import net.shattergui.links.ProjectLinks;
import net.shattergui.ModKeys;
import net.shattergui.ShatterMod;
import net.shattergui.config.ScreenKeys;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.Themes;
import net.shattergui.custom.CustomEffectDef;
import net.shattergui.editor.FxEditor;
import net.shattergui.editor.FxEditorScreen;
import net.shattergui.effect.EffectIds;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.game.FxFiles;
import net.shattergui.params.Param;
import net.shattergui.params.ParamStore;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.params.Preset;
import net.shattergui.params.Quality;
import net.shattergui.platform.EffectCatalog;
import net.shattergui.platform.GameInfo;
import net.shattergui.platform.KeyInput;
import net.shattergui.platform.Keys;
import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;
import net.shattergui.platform.TextLine;
import net.shattergui.ui.Button;
import net.shattergui.ui.Card;
import net.shattergui.ui.Column;
import net.shattergui.ui.ConfirmDialog;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Grid;
import net.shattergui.ui.Hint;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Label;
import net.shattergui.ui.Row;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Section;
import net.shattergui.ui.Segmented;
import net.shattergui.ui.Slider;
import net.shattergui.ui.Swatch;
import net.shattergui.ui.TabNav;
import net.shattergui.links.LinkStatus;
import net.shattergui.ui.links.LinkButton;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Toggle;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiClock;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;
import net.shattergui.ui.preview.PreviewPlayer;
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

    private final Object parent;
    private final ShatterConfig work;
    private final String startJson;
    private final List<CustomEffectDef> customs = new ArrayList<>();
    private final List<String> customsAtStart = new ArrayList<>();
    private final String savedAccent;

    private Tab tab = Tab.GENERAL;
    private enum PreviewMode {
        OPEN, CLOSE, BOTH
    }

    private PreviewMode previewMode = PreviewMode.BOTH;
    private String selectedEffect;
    private String selectedCustom;
    private String cloneFrom = "vortex";
    private boolean fullscreenIfSmall;
    private String selectedScreen;
    private String modFilter = ALL_MODS;
    private Text status = Text.empty();
    private int statusColor = Theme.SUCCESS;
    private double statusTime = -10;
    private boolean dirty;
    private int dirtyCountdown;
    private ParamValues frameValues;

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
    private FxEditor fxEditor;
    private boolean classicCustom;
    private final SmallNotice smallNotice = new SmallNotice();

    private int margin;
    private int headerBottom;
    private int footerTop;
    private int sideX;
    private int sideW;
    private int previewX;
    private int previewW;
    private int bodyTop;
    private int bodyBottom;
    private List<TextLine> captionLines = List.of();

    public ShatterConfigScreen(Object parent) {
        super(Text.translatable("screen.shatter_gui.title"));
        this.parent = parent;
        this.work = ShatterConfig.get().copy();
        this.startJson = work.toJsonString();
        this.savedAccent = ShatterConfig.get().uiAccent;
        Theme.setAccent(Theme.Accent.byId(work.uiAccent));
        Platform.get().capture().setRealPreviewConfigured(work.realPreview);
        for (CustomEffectDef def : Platform.get().effects().customFromConfig()) {
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

    private static Text tr(String key, Object... args) {
        return Text.translatable(key, args);
    }

    public ShatterConfig working() {
        return work;
    }

    public List<CustomEffectDef> customs() {
        return customs;
    }

    public CustomEffectDef selectedCustom() {
        return custom(selectedCustom);
    }

    public Text status() {
        return status;
    }

    public Tab tab() {
        return tab;
    }

    public boolean dirty() {
        return computeDirty();
    }

    public String describeThumbs() {
        StringBuilder sb = new StringBuilder();
        thumbs.forEach((key, player) -> sb.append(key).append('=').append(player.describe()).append("; "));
        return sb.toString();
    }

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

    public int[] tabCenter(Tab target) {
        return nav.center(target.ordinal());
    }

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
        int boxX = previewX + 6;
        int boxW = previewW - 12;
        int controlsH = Theme.CONTROL_HEIGHT + 20 + 16;
        previewModes.visible = tab == Tab.EFFECTS && !editorMode();
        if (previewModes.visible) {
            controlsH += (Theme.CONTROL_HEIGHT + 3) * 2 + 4;
        }
        int boxH = Math.max(40, Math.min(Math.round(boxW * 0.9f), bodyBottom - bodyTop - 24 - controlsH));
        liveBox.setBounds(boxX, bodyTop + 18, boxW, boxH);
        captionLines = Platform.get().texts().font().split(previewCaption(), Math.max(20, boxW)).stream().limit(2).toList();
        int cy = liveBox.y + boxH + 6 + captionLines.size() * Theme.LINE;
        if (previewModes.visible) {
            previewModes.layout(boxX, cy, boxW);
            cy += previewModes.h + 6;
        }
        autoplay.setBounds(previewX + previewW - 6 - Toggle.WIDTH, cy, Toggle.WIDTH, 14);
        cy += 20;
        int half = (boxW - 4) / 2;
        boolean iconsOnly = half < 82;
        replay.text(iconsOnly ? null : tr("ui.shatter_gui.replay"));
        inWorld.text(iconsOnly ? null : tr("ui.shatter_gui.in_world"));
        replay.setBounds(boxX, cy, half, Theme.CONTROL_HEIGHT);
        inWorld.setBounds(boxX + half + 4, cy, boxW - half - 4, Theme.CONTROL_HEIGHT);
        inWorld.enabled = inWorld();
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
            Draw.roundGradient(ui, wx + 8, wy + 5, 16, 16, 4, Theme.accentHover(), Theme.accentPressed());
            Draw.icon(ui, Icon.SPARKLES, wx + 12, wy + 9, 1, 0xFFFFFFFF);
            ui.text(tr("ui.shatter_gui.title"), wx + 30, wy + 6, Theme.TEXT);
            if (w >= 480) {
                ui.textSmall(tr("ui.shatter_gui.subtitle").getString(), wx + 30, wy + 16, ww / 2, Theme.TEXT_DIM);
            }
            Draw.rect(ui, wx + 1, headerBottom, ww - 2, 1, Theme.BORDER);
            Draw.roundRect(ui, sideX - 3, bodyTop, sideW + 6, bodyBottom - bodyTop, 6, Theme.SIDEBAR);
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

    private final class LiveBox extends Widget {
        @Override
        protected void draw(Ui ui) {
            Draw.roundRect(ui, x - 1, y - 1, w + 2, h + 2, 4, Theme.BORDER_STRONG);
            ui.pushClip(x, y, w, h);
            live.render(ui, x, y, w, h);
            if (tab == Tab.EFFECTS && previewMode == PreviewMode.OPEN && openValue(selectedEffect) == null) {
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

    private boolean editorMode() {
        return tab == Tab.CUSTOM && !classicCustom;
    }

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
            fullscreenIfSmall = false;
            if (!fits) {
                runLater(this::openEditorFullscreen);
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
        Platform.get().capture().releaseRealPreview();
    }

    private void setRealPreview(boolean value) {
        work.realPreview = value;
        Platform.get().capture().setRealPreviewConfigured(value);
    }

    public void openEditorFullscreen() {
        showScreen(new FxEditorScreen(this, effectEditor(), this::reattachEditor));
    }

    @Override
    protected boolean shortcut(KeyInput event) {
        return editorMode() && fxEditor != null && fxEditor.visible && fxEditor.shortcut(event);
    }

    private final class EditorHost implements FxEditor.Host {
        @Override
        public boolean fullscreen() {
            return currentScreen() instanceof FxEditorScreen;
        }

        @Override
        public void toggleFullscreen() {
            if (currentScreen() instanceof FxEditorScreen screen) {
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

    private Section section(Text title, Text subtitle) {
        String key = title.translationKey() != null ? title.translationKey() : title.getString();
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

        Section open = section(tr("ui.shatter_gui.open.title"), tr("ui.shatter_gui.open.desc"));
        open.add(choiceRow(thumb("general-open", () -> openScene(work.openEffect)),
                () -> openName(work.openEffect), () -> openDescription(work.openEffect),
                (Widget) new Dropdown<>(this::openOptions, () -> work.openEffect, v -> work.openEffect = v).thumbs(30, 22)
                        .tooltip(tr("ui.shatter_gui.open.title"), tr("ui.shatter_gui.open.desc"))));

        Section close = section(tr("ui.shatter_gui.close.title"), tr("ui.shatter_gui.close.desc"));
        close.add(choiceRow(thumb("general-close", () -> closeScene(known(work.effect), null, true, true)),
                () -> effectName(known(work.effect)), () -> effectDescription(known(work.effect)),
                (Widget) new Dropdown<>(this::closeOptions, () -> known(work.effect), v -> work.effect = v).thumbs(30, 22)
                        .tooltip(tr("ui.shatter_gui.close.title"), tr("ui.shatter_gui.close.desc"))));
        close.add(new SettingRow(tr("ui.shatter_gui.thematic"), tr("ui.shatter_gui.thematic.desc"), false,
                new Toggle(() -> work.thematic, v -> work.thematic = v), () -> work.thematic, () -> work.thematic = false));

        Section presets = section(tr("ui.shatter_gui.preset.title"), tr("ui.shatter_gui.preset.desc"));
        Grid grid = presets.add(new Grid(92, 6));
        for (Preset preset : Preset.values()) {
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
        playback.add(paramRow(Params.FOLLOW_CAMERA, work));
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

    private void buildAbout() {
        GameInfo.ModMeta meta = Platform.get().game().mod(ShatterMod.MOD_ID);
        if (meta == null) {
            return;
        }
        Section about = section(tr("ui.shatter_gui.about.title"), null);
        about.add(new Label(Text.literal(meta.name()), Label.Style.BODY));
        about.add(new Label(tr("ui.shatter_gui.about.version", meta.version()), Label.Style.DIM));
        about.add(new Label(tr("ui.shatter_gui.about.build", net.shattergui.BuildInfo.describe()), Label.Style.SMALL));
        about.add(new Label(() -> {
            net.shattergui.platform.SpatialGuiBridge.Status spatial = Platform.get().spatialGui().status();
            return tr(spatial.reason().key(), spatial.versionText(), spatial.detail());
        }, Label.Style.SMALL));
        String authors = String.join(", ", meta.authors());
        about.add(new Label(tr("ui.shatter_gui.about.author", authors), Label.Style.DIM));
        about.add(new Label(tr("ui.shatter_gui.about.license", String.join(", ", meta.licenses())), Label.Style.DIM));
        about.add(new Label(tr("ui.shatter_gui.about.link", ProjectLinks.SOURCES), Label.Style.DIM));
        about.add(new SettingRow(tr("ui.shatter_gui.link_status_check"), tr("ui.shatter_gui.link_status_check.desc"), false,
                new Toggle(() -> work.linkStatusCheck, v -> work.linkStatusCheck = v), () -> !work.linkStatusCheck,
                () -> work.linkStatusCheck = true));
        List<Button> pages = new ArrayList<>();
        Object[][] pageLinks = {{"modrinth", "modrinth", LinkButton.Brand.MODRINTH},
                {"curseforge", "curseforge", LinkButton.Brand.CURSEFORGE}, {"sources", "github", LinkButton.Brand.GITHUB},
                {"issues", "report_bug", LinkButton.Brand.BUG}};
        for (Object[] page : pageLinks) {
            String url = ProjectLinks.contact().get((String) page[0]);
            String key = "ui.shatter_gui.about." + page[1];
            LinkButton open = new LinkButton((LinkButton.Brand) page[2], tr(key), tr(key + ".tooltip"),
                    () -> work.linkStatusCheck, meta.version());
            open.action(() -> openPage(open, url)).leavesScreen(true);
            pages.add(open);
        }
        Button diagnostics = new Button(tr("ui.shatter_gui.about.copy_diagnostics"), Icon.COPY, Button.Kind.SECONDARY,
                this::copyDiagnostics);
        diagnostics.tooltip(tr("ui.shatter_gui.about.copy_diagnostics"), tr("ui.shatter_gui.about.copy_diagnostics.tooltip"));
        pages.add(diagnostics);
        Grid links = about.add(new Grid(pages.stream().mapToInt(Button::naturalWidth).max().orElse(80), 6));
        pages.forEach(links::add);
        about.add(new Hint(tr("ui.shatter_gui.about.translations")));
        about.add(new Hint(tr("ui.shatter_gui.about.third_party")));
    }

    private void openPage(LinkButton button, String url) {
        if (button.state() == LinkStatus.State.NOT_PUBLISHED) {
            ConfirmDialog.open(tr("ui.shatter_gui.about.not_published.title"),
                    tr("ui.shatter_gui.about.not_published.message"), tr("ui.shatter_gui.about.not_published.open"), false,
                    () -> confirmLink(url));
            return;
        }
        confirmLink(url);
    }

    private Row choiceRow(PreviewPlayer player, Supplier<Text> name, Supplier<Text> description, Widget dropdown) {
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
            settings.add(new Hint(tr("ui.shatter_gui.effects.custom_note", Platform.get().effects().displayName(def.base()))));
            addCustomParamRows(settings, def);
            return;
        }
        if (packDef(id) != null) {
            settings.add(new Hint(tr("ui.shatter_gui.effects.pack_note")));
            return;
        }
        EffectCatalog.Data data = dataEffect(id);
        if (data != null && !data.bundled()) {
            addDataInfo(settings, data);
            return;
        }
        List<Param<?>> params = Params.group(id);
        if (data != null) {
            settings.add(new Hint(tr(data.def().openable ? "ui.shatter_gui.effects.bundled_note.open" : "ui.shatter_gui.effects.bundled_note")));
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
        Map<String, GameInfo.ScreenEntry> screens = allScreens();
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (GameInfo.ScreenEntry entry : screens.values()) {
            if (!ALL_MODS.equals(modFilter) && !modFilter.equals(entry.modId())) {
                continue;
            }
            String title = screenTitle(entry);
            String mod = Platform.get().game().modName(entry.modId());
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

    private Card screenCard(GameInfo.ScreenEntry entry, String title, String mod) {
        String key = entry.key();
        Card card = new Card(8, 6).onClick(() -> selectScreen(key)).selected(() -> key.equals(selectedScreen));
        Row header = card.add(new Row(8));
        header.fixed(new IconBox(key, entry.modId()), 18);
        Column names = new Column(0, 2);
        names.add(new Label(Text.literal(title), Label.Style.BODY));
        names.add(new Label(Text.literal(mod + "  ·  " + key), Label.Style.SMALL));
        header.flex(names, 1);
        card.tooltip(Text.literal(title), tr("ui.shatter_gui.screens.key", key, mod));
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
            Platform.get().game().drawScreenIcon(ui, key, modId, x + 1, y + 1, 16);
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    private void buildCustom() {
        if (!classicCustom) {
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
        Section details = content.add(new Section("settings.custom_details", Icon.STAR, () -> Text.literal(def.name()), false, 7));
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
        details.add(new Label(() -> tr("ui.shatter_gui.custom.info", Platform.get().effects().displayName(def.base()), def.id(),
                def.particles().size(), def.sounds().size()), Label.Style.DIM));
        Row resetRow = details.add(new Row(6));
        resetRow.flex(new Label(tr("ui.shatter_gui.custom.params"), Label.Style.BODY), 1);
        Button resetAll = new Button(tr("ui.shatter_gui.reset_all"), Icon.RESET, Button.Kind.GHOST, def::resetParams);
        resetAll.tooltip(tr("ui.shatter_gui.reset_all"), tr("ui.shatter_gui.custom.reset.tooltip"));
        resetRow.fixed(resetAll, resetAll.naturalWidth());
        addCustomParamRows(details, def);
        details.add(new Hint(tr("ui.shatter_gui.custom.later")));
    }

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

    private static Text help(Param<?> param) {
        Text range = switch (param) {
            case Param.Float p -> tr("ui.shatter_gui.range", ParamUi.format(p, p.min()), ParamUi.format(p, p.max()),
                    ParamUi.format(p, p.def()));
            case Param.Int p -> tr("ui.shatter_gui.range", ParamUi.format(p, p.min()), ParamUi.format(p, p.max()),
                    ParamUi.format(p, p.def()));
            case Param.Bool p -> tr("ui.shatter_gui.default", tr(p.def() ? "options.on" : "options.off"));
            case Param.Choice p -> tr("ui.shatter_gui.default", tr(p.choiceKey(p.def())));
        };
        return Text.translatable(param.langKey() + ".tooltip").copy().append("\n").append(range);
    }

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

    private static String openValue(String id) {
        boolean fly = Platform.get().effects().isFlyBased(id);
        return ShatterConfig.openValueFor(id, Platform.get().effects().canOpen(id), fly);
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

    private void stopUsing(String id) {
        if (usedForOpen(id)) {
            work.openEffect = ShatterConfig.OPEN_NONE;
        }
        if (usedForClose(id) && !EffectIds.GLASS.equals(id)) {
            work.effect = EffectIds.GLASS;
        }
        setStatus(tr("ui.shatter_gui.assign.done.removed", effectName(id)), true);
    }

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
                enabled = usedForOpen(id) || usedForClose(id) && !EffectIds.GLASS.equals(id);
                super.draw(ui);
            }
        };
        remove.tooltip(tr("ui.shatter_gui.assign.remove"), tr("ui.shatter_gui.assign.remove.tooltip"));
        List<Button> buttons = List.of(open, close, both, remove);
        Grid row = card.add(new Grid(buttons.stream().mapToInt(Button::naturalWidth).max().orElse(60), 4));
        buttons.forEach(row::add);
    }

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
            return mouseDown(mx, my, 0);
        }

        @Override
        public boolean key(KeyInput event) {
            if (event.key() == Keys.KEY_RETURN
                    || event.key() == Keys.KEY_SPACE) {
                click();
                toggleFavorite(id);
                return true;
            }
            return false;
        }
    }

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
        public Text tooltipTitle() {
            return usedForOpen(id) || usedForClose(id) ? tr("ui.shatter_gui.badge.title") : null;
        }

        @Override
        public Text tooltipText() {
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

    private List<Dropdown.Option<String>> openOptions() {
        List<Dropdown.Option<String>> options = new ArrayList<>();
        List<String> ids = new ArrayList<>(OPEN_IDS);
        for (String id : Platform.get().effects().openIds()) {
            ids.add(ShatterConfig.OPEN_DATA_PREFIX + id);
        }
        for (String id : ids) {
            options.add(new Dropdown.Option<>(id, openName(id), openDescription(id), painter("open-" + id, () -> openScene(id))));
        }
        return options;
    }

    private static final java.util.Set<String> OWN_OPEN_NAMES = java.util.Set.of(EffectIds.BURN);

    private Text openName(String open) {
        String id = ShatterConfig.openDataId(open);
        if (id != null && OWN_OPEN_NAMES.contains(id)) {
            return tr("ui.shatter_gui.open.effect." + id);
        }
        return id != null ? tr("ui.shatter_gui.open.data", effectName(id)) : tr("ui.shatter_gui.open." + open);
    }

    private Text openDescription(String open) {
        String id = ShatterConfig.openDataId(open);
        if (id != null) {
            if (!Platform.get().effects().canOpen(id)) {
                return tr("ui.shatter_gui.open.data.missing");
            }
            return OWN_OPEN_NAMES.contains(id) ? tr("ui.shatter_gui.open.effect." + id + ".desc")
                    : tr("ui.shatter_gui.open.data.desc", effectName(id));
        }
        return tr("ui.shatter_gui.open." + open + ".desc");
    }

    public void cloneBundled(String id) {
        String json = Platform.get().effects().bundledJson(id);
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
        for (GameInfo.ScreenEntry entry : allScreens().values()) {
            mods.add(entry.modId());
        }
        for (String mod : mods) {
            options.add(new Dropdown.Option<>(mod, Text.literal(Platform.get().game().modName(mod))));
        }
        return options;
    }

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

    private Scene closeScene(String id, String open, boolean hasSource, boolean edgeAllowed) {
        EffectCatalog.Data data = dataEffect(id);
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
        return new Scene(open, Platform.get().effects().isBuiltIn(id) ? id : EffectIds.GLASS, frameValues, null, hasSource, edgeAllowed);
    }

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
                boolean container = selectedScreen.contains(":") && !ScreenKeys.INVENTORY.equals(selectedScreen);
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

    private Text previewCaption() {
        return switch (tab) {
            case EFFECTS -> tr("ui.shatter_gui.preview.effect", effectName(selectedEffect));
            case CUSTOM -> selectedCustom != null ? tr("ui.shatter_gui.preview.effect", effectName(selectedCustom))
                    : tr("ui.shatter_gui.preview.general");
            case SCREENS -> {
                if (selectedScreen == null) {
                    yield tr("ui.shatter_gui.preview.pick_screen");
                }
                GameInfo.ScreenEntry entry = allScreens().get(selectedScreen);
                yield tr("ui.shatter_gui.preview.screen", screenTitle(entry != null ? entry
                        : new GameInfo.ScreenEntry(selectedScreen, "", "?", "")));
            }
            default -> tr("ui.shatter_gui.preview.general");
        };
    }

    private Map<String, GameInfo.ScreenEntry> allScreens() {
        Map<String, GameInfo.ScreenEntry> screens = new LinkedHashMap<>();
        for (GameInfo.ScreenEntry entry : Platform.get().game().screens()) {
            screens.put(entry.key(), entry);
        }
        for (String key : Platform.get().game().seenThisSession()) {
            screens.putIfAbsent(key, new GameInfo.ScreenEntry(key, "", Platform.get().game().ownerOfKey(key), key));
        }
        for (String key : work.overrides.keySet()) {
            screens.putIfAbsent(key, new GameInfo.ScreenEntry(key, "", Platform.get().game().ownerOfKey(key), key));
        }
        for (String key : work.disabledScreens) {
            screens.putIfAbsent(key, new GameInfo.ScreenEntry(key, "", Platform.get().game().ownerOfKey(key), key));
        }
        return screens;
    }

    private static String screenTitle(GameInfo.ScreenEntry entry) {
        if (ScreenKeys.INVENTORY.equals(entry.key())) {
            return Text.translatable("container.inventory").getString();
        }
        if (entry.title() != null && !entry.title().isBlank()) {
            return entry.title();
        }
        String key = entry.key();
        String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key.substring(key.lastIndexOf('.') + 1);
        name = name.replace("Screen", "").replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return name.isEmpty() ? key : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

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

    private List<String> effectIds() {
        List<String> ids = new ArrayList<>(Platform.get().effects().builtInIds());
        for (CustomEffectDef def : customs) {
            ids.add(def.id());
        }
        for (CustomEffectDef def : Platform.get().effects().customFromPacks()) {
            if (!ids.contains(def.id())) {
                ids.add(def.id());
            }
        }
        for (String id : Platform.get().effects().dataIds()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static EffectCatalog.Data dataEffect(String id) {
        return Platform.get().effects().data(id);
    }

    private String known(String id) {
        return effectIds().contains(id) ? id : EffectIds.GLASS;
    }

    private Text effectName(String id) {
        CustomEffectDef def = custom(id);
        return def != null ? Text.literal(def.name()) : Platform.get().effects().displayName(id);
    }

    private Text effectDescription(String id) {
        EffectCatalog.Data data = dataEffect(id);
        if (data != null && !data.bundled()) {
            return data.def().description.isEmpty() ? tr("ui.shatter_gui.effect.data.desc") : Text.literal(data.def().description);
        }
        CustomEffectDef def = custom(id);
        if (def == null) {
            def = packDef(id);
        }
        if (def != null) {
            return tr("ui.shatter_gui.effect.custom.desc", Platform.get().effects().displayName(def.base()));
        }
        return tr("ui.shatter_gui.effect." + id + ".desc");
    }

    private Text effectShort(String id) {
        if (dataEffect(id) != null && !dataEffect(id).bundled()) {
            return tr("ui.shatter_gui.effect.data.short");
        }
        CustomEffectDef def = custom(id);
        if (def == null) {
            def = packDef(id);
        }
        if (def != null) {
            return tr("ui.shatter_gui.effect.custom.short", Platform.get().effects().displayName(def.base()));
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
        for (CustomEffectDef def : Platform.get().effects().customFromPacks()) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

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
            created = new CustomEffectDef(freeId(from), tr("ui.shatter_gui.custom.copy_of", Platform.get().effects().displayName(from)).getString(), from);
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
            work.effect = EffectIds.GLASS;
        }
        if (def.id().equals(selectedEffect)) {
            selectedEffect = known(work.effect);
        }
        selectedCustom = customs.isEmpty() ? null : customs.getFirst().id();
        setStatus(tr("ui.shatter_gui.custom.deleted", def.name()), true);
        rebuild();
    }

    public String copyDiagnostics() {
        ShatterConfig saved = ShatterConfig.get();
        String text = net.shattergui.debug.Diagnostics.build(Platform.get().game().diagnostics(), saved.openEffect,
                saved.effect, saved.thematic, net.shattergui.debug.RecentWarnings.all());
        Platform.get().desktop().setClipboard(text);
        setStatus(tr("ui.shatter_gui.about.diagnostics_copied"), true);
        return text;
    }

    public String exportSelected() {
        CustomEffectDef def = custom(selectedCustom);
        if (def == null) {
            return null;
        }
        String code = def.toCode();
        Platform.get().desktop().setClipboard(code);
        setStatus(tr("ui.shatter_gui.custom.exported", def.name(), code.length()), true);
        return code;
    }

    public CustomEffectDef importClipboard() {
        String clipboard = Platform.get().desktop().clipboard();
        String json = FxFiles.fromCode(clipboard);
        if (json != null && FxParser.schemaOf(json) == FxLimits.SCHEMA) {
            FxProblems problems = Platform.get().effects().importJson(json);
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

    public void reloadEffects() {
        Platform.get().effects().reloadCustom();
        Platform.get().effects().reloadAll();
        thumbs.clear();
        long errors = Platform.get().effects().messages().stream().filter(m -> m.startsWith("error")).count();
        setStatus(tr("ui.shatter_gui.custom.reloaded", Platform.get().effects().dataIds().size(), errors), errors == 0);
        rebuild();
    }

    public String exportData(String id) {
        EffectCatalog.Data data = dataEffect(id);
        String code = data == null ? null : Platform.get().effects().toCode(data);
        if (code == null) {
            setStatus(tr("ui.shatter_gui.custom.export_package"), false);
            return null;
        }
        Platform.get().desktop().setClipboard(code);
        setStatus(tr("ui.shatter_gui.custom.exported", data.def().name, code.length()), true);
        return code;
    }

    private void buildDataSection() {
        Section card = section(tr("ui.shatter_gui.data.title"), tr("ui.shatter_gui.data.desc"));
        List<String> ids = new ArrayList<>(Platform.get().effects().dataIds());
        ids.removeIf(Platform.get().effects()::isBundled);
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
        List<String> messages = Platform.get().effects().messages();
        if (!messages.isEmpty()) {
            card.add(new Label(tr("ui.shatter_gui.data.messages", messages.size()), Label.Style.BODY));
            for (String message : messages.subList(0, Math.min(12, messages.size()))) {
                card.add(new Label(Text.literal("• " + message), Label.Style.DIM));
            }
        }
    }

    private void addDataInfo(net.shattergui.ui.Container card, EffectCatalog.Data data) {
        card.add(new Label(tr("ui.shatter_gui.data.info", data.def().fracture.mode(), data.def().trigger.type(),
                data.def().layers.size(), data.def().emitters.size(), data.def().duration / 20f), Label.Style.DIM));
        card.add(new Label(tr("ui.shatter_gui.data.origin", data.origin()), Label.Style.SMALL));
        if (!data.def().textures.isEmpty()) {
            card.add(new Label(tr("ui.shatter_gui.data.textures", String.join(", ", data.loadedTextures()),
                    data.def().textures.size()), Label.Style.DIM));
        }
        card.add(new Hint(tr("ui.shatter_gui.data.edit_note")));
        if (data.def().textures.isEmpty()) {
            Button export = new Button(tr("ui.shatter_gui.custom.export"), Icon.EXPORT, Button.Kind.SECONDARY,
                    () -> exportData(data.def().id));
            export.tooltip(tr("ui.shatter_gui.custom.export"), tr("ui.shatter_gui.custom.export.tooltip"));
            Row row = card.add(new Row(6));
            row.fixed(export, export.naturalWidth());
        }
    }

    private String freeId(String wanted) {
        String base = wanted.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isEmpty()) {
            base = "custom";
        }
        base = base.length() > 28 ? base.substring(0, 28) : base;
        String id = base;
        for (int n = 2; Platform.get().effects().isReserved(id) || custom(id) != null || packDef(id) != null
                || OPEN_IDS.contains(id) || DEFAULT.equals(id) || OFF.equals(id); n++) {
            id = base + "_" + n;
        }
        return id;
    }

    private void setStatus(Text text, boolean ok) {
        status = text;
        statusColor = ok ? Theme.SUCCESS : Theme.DANGER;
        statusTime = UiClock.time();
    }

    public void preview() {
        String id = switch (tab) {
            case CUSTOM -> selectedCustom != null ? selectedCustom : selectedEffect;
            case EFFECTS -> selectedEffect;
            default -> known(work.effect);
        };
        if (!Platform.get().capture().playInWorld(this, id, custom(id), work.values())) {
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
            Platform.get().effects().saveCustom(customs);
        }
        boolean wasEnabled = ShatterConfig.get().enabled;
        ShatterConfig.replace(work.copy());
        Theme.setAccent(Theme.Accent.byId(work.uiAccent));
        if (wasEnabled && !work.enabled) {
            ModKeys.setEnabled(false);
        }
        showScreen(parent);
    }

    @Override
    protected void escape() {
        if (computeDirty()) {
            ConfirmDialog.open(tr("ui.shatter_gui.discard.title"), tr("ui.shatter_gui.discard.message"),
                    tr("ui.shatter_gui.discard.confirm"), true, this::onClose);
        } else {
            onClose();
        }
    }

    @Override
    public void onClose() {
        disposeEditor();
        Platform.get().capture().setRealPreviewConfigured(ShatterConfig.get().realPreview);
        Theme.setAccent(Theme.Accent.byId(savedAccent));
        showScreen(parent);
    }
}
