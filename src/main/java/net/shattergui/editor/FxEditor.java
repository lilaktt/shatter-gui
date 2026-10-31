package net.shattergui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.locale.Language;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.PreviewRunner;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.FxSchema;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.params.ParamValues;
import net.shattergui.ui.ConfirmDialog;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.TabNav;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiClock;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;
import net.shattergui.ui.preview.SampleImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The visual editor of schema-2 effects, made of the mod's own widgets. Top: the effects menu, name, Save,
 * Undo/Redo, Test, Export, Import, folder, full screen. Left: the effect's parts (layers, emitters, sounds) or the
 * texture library. Middle: the live preview with its transport. Right: the inspector. Bottom: the timeline and a
 * line with the limits as counters (pieces, particles, layers, textures, size) and the last message.
 *
 * <p>The effect is edited as its JSON ({@link FxDoc}): every change is an undo step (Ctrl+Z, Ctrl+Y), checked at once
 * (the parser plus {@link EditorChecks}), and shown by the preview; while there are errors the preview keeps the last
 * valid state. Save writes {@code effects/<id>.json} atomically and reloads the effects so the game uses it.
 * Nothing the player types can break it: odd values are clamped or reported next to their field.
 */
public final class FxEditor extends Container {
    /** What the screen around the editor offers. */
    public interface Host {
        boolean fullscreen();

        void toggleFullscreen();

        /** True if the classic (format 1) effects can be shown instead (only inside the settings). */
        boolean canShowClassic();

        void showClassic();
    }

    public final FxDoc doc;
    public final EffectFiles files;
    final TextureLibrary library;
    final EditorPreview preview;
    final Supplier<ParamValues> values;
    private final Host host;
    private final EditorState state;

    private final TopBar top;
    private final TabNav leftTabs;
    private final LayerPanel layers;
    private final TexturePanel textures;
    private final PreviewPane previewPane;
    private final Inspector inspector;
    private final Timeline timeline;
    private final Decor decor = new Decor();

    private Selection selection = Selection.EFFECT;
    private String fileId;
    private int builtVersion = -1;
    private Selection builtSelection;
    private boolean inspectorDirty = true;
    private int checkedVersion = -1;
    private int checkedLibrary = -1;
    private List<FxProblems.Problem> problems = List.of();
    private Component status = Component.empty();
    private int statusColor = Theme.SUCCESS;
    private double statusTime = -10;
    private DataEffect testEffect;
    private boolean tourChecked;
    private int leftTab;
    private long lastFrame = -1;
    private long arrangedFrame = -10;
    private boolean reportedFailure;

    public FxEditor(Host host, Supplier<ParamValues> values) {
        this.host = host;
        this.values = values;
        this.files = new EffectFiles(FxEffects.EFFECTS, FxEffects.TEXTURES, FxEffects.EXPORTS, FxEffects.PACKAGES);
        this.state = EditorState.load(FxEffects.CONFIG.resolve("editor.json"));
        this.library = new TextureLibrary(FxEffects.TEXTURES);
        this.preview = new EditorPreview(values, library::tex);
        for (SampleImage.Kind kind : SampleImage.Kind.values()) {
            if (kind.id().equals(state.sample)) {
                preview.setSample(kind);
            }
        }
        this.doc = new FxDoc(Templates.json("glass"));
        add(decor);
        top = add(new TopBar(this));
        leftTabs = add(new TabNav(false, i -> leftTab = i));
        leftTabs.add(net.shattergui.ui.Icon.LAYERS, EditorUi.tr("left.layers"), EditorUi.tr("left.layers.desc"))
                .add(net.shattergui.ui.Icon.IMAGE, EditorUi.tr("left.textures"), EditorUi.tr("left.textures.desc"));
        layers = add(new LayerPanel(this));
        textures = add(new TexturePanel(this));
        previewPane = add(new PreviewPane(this));
        inspector = add(new Inspector(this));
        timeline = add(new Timeline(this));
        library.rescan();
        openInitial();
    }

    private void openInitial() {
        List<String> ids = files.ids();
        String id = ids.contains(state.lastEffect) ? state.lastEffect : ids.isEmpty() ? null : ids.getFirst();
        if (id != null) {
            open(id);
        } else {
            newEffect("glass");
        }
    }

    Host host() {
        return host;
    }

    /** The id written in the effect being edited (saved or not). */
    public String docId() {
        return doc.str("id", "");
    }

    public String fileId() {
        return fileId;
    }

    public Selection selection() {
        return selection;
    }

    // --- layout and frame ---

    @Override
    protected void arrange() {
        // Layout runs once per frame before drawing: the per-frame work goes first, so a rebuilt inspector is laid out
        // before it is drawn.
        if (lastFrame != UiClock.frameNumber()) {
            lastFrame = UiClock.frameNumber();
            try {
                frame();
            } catch (RuntimeException e) {
                failed(e);
            }
        }
        arrangedFrame = UiClock.frameNumber();
        int gap = 4;
        int topH = 20;
        int statusH = 11;
        boolean small = h < 300;
        int timelineH = Math.clamp(Math.round(h * 0.24f), small ? 48 : 60, 150);
        int leftW = Math.clamp(Math.round(w * 0.2f), 124, 200);
        int rightW = Math.clamp(Math.round(w * 0.3f), 150, 300);
        int middleY = y + topH + gap;
        int middleH = Math.max(40, h - topH - gap - timelineH - gap - statusH - 2);
        top.setBounds(x, y, w, topH);
        leftTabs.layout(x, middleY, leftW);
        leftTabs.compact(leftW < 130);
        int panelY = middleY + leftTabs.h + 3;
        int panelH = middleY + middleH - panelY;
        layers.visible = leftTab == 0;
        textures.visible = leftTab == 1;
        layers.setBounds(x, panelY, leftW, panelH);
        textures.setBounds(x, panelY, leftW, panelH);
        int centerX = x + leftW + gap + 2;
        int centerW = Math.max(60, w - leftW - rightW - gap * 2 - 4);
        previewPane.setBounds(centerX, middleY, centerW, middleH);
        inspector.setBounds(x + w - rightW, middleY, rightW, middleH);
        timeline.setBounds(x, middleY + middleH + gap, w, timelineH);
    }

    @Override
    protected void draw(Ui ui) {
        try {
            super.draw(ui);
            drawStatus(ui);
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    /** Something in the editor broke: logged once, said in the status line; the game goes on. */
    private void failed(RuntimeException e) {
        reportedFailure = true;
        net.shattergui.ui.UiFailures.report("frame FxEditor", e);
        status(EditorUi.tr("status.internal", String.valueOf(e.getMessage())), false);
        refreshInspector();
    }

    /** Once per frame: the preview follows the effect; the inspector is rebuilt when needed; the tour starts. */
    private void frame() {
        FxParser.Result result = doc.result();
        if (result.ok()) {
            preview.setDef(result.def());
        }
        preview.frame(UiClock.dt());
        if (inspectorDirty || builtSelection == null || !builtSelection.equals(selection)) {
            inspectorDirty = false;
            builtSelection = selection;
            builtVersion = doc.version();
            inspector.rebuild();
        }
        if (!tourChecked && UiScreen.active() != null) {
            tourChecked = true;
            if (!state.tourDone) {
                startTour();
            }
        }
    }

    /** The backgrounds of the panels. */
    private final class Decor extends Widget {
        @Override
        protected void draw(Ui ui) {
            Draw.roundRect(ui, layersBoxX(), leftTabs.y - 2, leftTabs.w + 4, layers.y + layers.h - leftTabs.y + 4, 5, Theme.SIDEBAR);
            Draw.roundBox(ui, inspector.x - 3, inspector.y - 2, inspector.w + 3, inspector.h + 4, 5, Theme.SIDEBAR, Theme.BORDER);
        }

        private int layersBoxX() {
            return layers.x - 2;
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    private void drawStatus(Ui ui) {
        int sy = y + h - 10;
        double since = UiClock.time() - statusTime;
        int countersRight = x + w;
        if (since < 5) {
            float a = (float) Math.min(1, Math.min(since * 6, (5 - since) * 2));
            int sw = Math.min(ui.font.width(status), w / 2);
            ui.textFit(status, x + w - sw, sy, sw, Theme.fade(statusColor, a));
            countersRight -= sw + 8;
        }
        int cx = x + 2;
        for (Counter c : counters()) {
            String text = c.label().getString() + " " + c.value() + "/" + c.max();
            int tw = ui.font.width(text);
            if (cx + tw > countersRight) {
                break;
            }
            float f = c.max() == 0 ? 0 : (float) c.value() / c.max();
            int color = f >= 1 ? 0xFFFCA5A5 : f >= 0.75f ? 0xFFFCD34D : Theme.TEXT_DIM;
            Draw.circle(ui, cx + 2, sy + 3, 2, f >= 1 ? Theme.DANGER : f >= 0.75f ? Theme.WARNING : Theme.SUCCESS);
            ui.text(text, cx + 7, sy, color);
            cx += tw + 16;
        }
    }

    /** A limit and how much of it the effect uses. */
    record Counter(Component label, long value, long max) {
    }

    List<Counter> counters() {
        List<Counter> out = new ArrayList<>();
        var sim = preview.sim();
        int pieces = sim == null ? 0 : sim.simulation().pieces().size();
        int triangles = sim == null ? 0 : sim.triangleCount();
        out.add(new Counter(EditorUi.tr("count.pieces"), pieces, FxLimits.MAX_PIECES));
        out.add(new Counter(EditorUi.tr("count.triangles"), triangles, FxLimits.MAX_TRIANGLES));
        out.add(new Counter(EditorUi.tr("count.particles"), preview.peakSprites(), FxLimits.MAX_SPRITES_PER_WAVE));
        out.add(new Counter(EditorUi.tr("count.layers"), doc.size("layers"), FxLimits.MAX_LAYERS));
        out.add(new Counter(EditorUi.tr("count.emitters"), doc.size("emitters"), FxLimits.MAX_EMITTERS));
        Set<String> used = new LinkedHashSet<>();
        long bytes = 0;
        for (EditorChecks.TextureRef ref : EditorChecks.textureRefs(doc)) {
            if (used.add(ref.name())) {
                TextureLibrary.Entry e = library.get(ref.name());
                bytes += e == null ? 0 : e.bytes();
            }
        }
        out.add(new Counter(EditorUi.tr("count.textures"), used.size(), FxLimits.MAX_TEXTURES));
        out.add(new Counter(EditorUi.tr("count.texture_kb"), bytes / 1024, FxLimits.MAX_TEXTURE_BYTES / 1024));
        out.add(new Counter(EditorUi.tr("count.json_kb"), doc.json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length / 1024,
                FxLimits.MAX_JSON_BYTES / 1024));
        return out;
    }

    // --- problems ---

    /** Parser problems plus the editor's own checks (cached until the effect or the textures change). */
    public List<FxProblems.Problem> problems() {
        if (checkedVersion != doc.version() || checkedLibrary != library.version()) {
            checkedVersion = doc.version();
            checkedLibrary = library.version();
            List<FxProblems.Problem> all = new ArrayList<>(doc.result().problems().all());
            try {
                all.addAll(EditorChecks.check(this));
            } catch (RuntimeException e) {
                all.add(new FxProblems.Problem(false, "", "check failed: " + e));
            }
            problems = all;
        }
        return problems;
    }

    /** Problems of a field (exact: only that path; otherwise also everything inside it). */
    public List<FxProblems.Problem> problemsAt(String path, boolean exact) {
        List<FxProblems.Problem> out = new ArrayList<>();
        for (FxProblems.Problem p : problems()) {
            if (exact ? p.path().equals(path) : FxDoc.isUnder(p.path(), path)) {
                out.add(p);
            }
        }
        return out;
    }

    public int errorCount() {
        return (int) problems().stream().filter(FxProblems.Problem::error).count();
    }

    boolean maskFallback() {
        return preview.sim() != null && preview.sim().simulation().maskMissing();
    }

    // --- selection ---

    public void select(Selection s) {
        if (s.list() != null && (s.index() < 0 || s.index() >= doc.size(s.list()))) {
            s = Selection.EFFECT;
        }
        if (!s.equals(selection)) {
            selection = s;
            inspectorDirty = true;
        }
    }

    /** The inspector is rebuilt in the next frame (a form or the effect's shape changed). */
    public void refreshInspector() {
        inspectorDirty = true;
    }

    void showCurve(String path, FxSchema.Field field, JsonElement def, String window) {
        Component item = window == null ? EditorUi.tr("part.appearance")
                : LayerPanel.itemName(doc, window.startsWith("emitters") ? Selection.Kind.EMITTER : Selection.Kind.LAYER,
                Integer.parseInt(window.substring(window.indexOf('[') + 1, window.indexOf(']'))));
        String param = path.substring(path.lastIndexOf('.') + 1);
        timeline.showCurve(path, field, def, window, Component.literal(item.getString() + " · " + param));
    }

    // --- list items ---

    public int addLayer(FxDef.Module module) {
        JsonObject layer = new JsonObject();
        layer.addProperty("name", EditorUi.moduleName(module).getString());
        layer.addProperty("module", FxSchema.moduleName(module));
        if (module == FxDef.Module.FOLLOW_PATH) {
            JsonObject params = new JsonObject();
            JsonArray points = new JsonArray();
            points.add(pair(0.15f, -0.15f));
            points.add(pair(0.3f, 0.05f));
            params.add("points", points);
            layer.add("params", params);
        }
        int index = doc.append("layers", layer);
        select(new Selection(Selection.Kind.LAYER, index));
        status(EditorUi.tr("status.added", EditorUi.moduleName(module)), true);
        return index;
    }

    private static JsonArray pair(float a, float b) {
        JsonArray p = new JsonArray();
        p.add(FxDoc.number(a));
        p.add(FxDoc.number(b));
        return p;
    }

    public int addEmitter(boolean sprite) {
        JsonObject emitter = new JsonObject();
        if (sprite) {
            String texture = textures.chosen() != null ? textures.chosen() : firstTexture();
            emitter.addProperty("sprite", texture);
        } else {
            emitter.addProperty("particle", "minecraft:end_rod");
        }
        emitter.addProperty("rate", 1);
        int index = doc.append("emitters", emitter);
        select(new Selection(Selection.Kind.EMITTER, index));
        return index;
    }

    public int addSound() {
        JsonObject sound = new JsonObject();
        sound.addProperty("sound", "minecraft:block.glass.break");
        sound.addProperty("volume", 0.6);
        int index = doc.append("sounds", sound);
        select(new Selection(Selection.Kind.SOUND, index));
        return index;
    }

    public void addOverlay(String texture) {
        if (doc.size("appearance.overlays") >= FxLimits.MAX_OVERLAYS) {
            status(EditorUi.tr("status.overlays_full", FxLimits.MAX_OVERLAYS), false);
            return;
        }
        JsonObject overlay = new JsonObject();
        overlay.addProperty("texture", texture != null ? texture : firstTexture());
        overlay.addProperty("blend", "additive");
        overlay.addProperty("opacity", 0.6);
        doc.append("appearance.overlays", overlay);
        select(Selection.APPEARANCE);
        refreshInspector();
    }

    private String firstTexture() {
        for (TextureLibrary.Entry e : library.entries()) {
            if (e.ok()) {
                return e.name();
            }
        }
        return "texture";
    }

    public void duplicate(Selection s) {
        if (s.list() == null) {
            return;
        }
        int max = switch (s.kind()) {
            case LAYER -> FxLimits.MAX_LAYERS;
            case EMITTER -> FxLimits.MAX_EMITTERS;
            default -> FxLimits.MAX_SOUNDS;
        };
        if (doc.size(s.list()) >= max) {
            status(EditorUi.tr("status.list_full"), false);
            return;
        }
        int copy = doc.duplicate(s.list(), s.index());
        if (copy >= 0) {
            select(new Selection(s.kind(), copy));
            refreshInspector();
        }
    }

    public void removeItem(Selection s) {
        if (s.list() == null) {
            return;
        }
        Component name = LayerPanel.itemName(doc, s.kind(), s.index());
        doc.remove(s.path());
        if (timeline.lanePath() != null && timeline.lanePath().startsWith(s.path())) {
            timeline.hideCurve();
        }
        int left = doc.size(s.list());
        select(left == 0 ? Selection.EFFECT : new Selection(s.kind(), Math.min(s.index(), left - 1)));
        refreshInspector();
        status(EditorUi.tr("status.removed", name), true);
    }

    public void moveItem(Selection s, int to) {
        doc.move(s.list(), s.index(), to);
        timeline.hideCurve();
        select(new Selection(s.kind(), to));
        refreshInspector();
    }

    public void toggleEdge() {
        if (doc.has("modifiers.edge")) {
            doc.edit(null, r -> {
                FxDoc.delete(r, "modifiers.edge");
                JsonElement modifiers = FxDoc.get(r, "modifiers");
                if (modifiers != null && modifiers.isJsonObject() && modifiers.getAsJsonObject().isEmpty()) {
                    r.remove("modifiers");
                }
            });
        } else {
            JsonObject edge = new JsonObject();
            edge.addProperty("direction", "random");
            edge.addProperty("exit", true);
            doc.set("modifiers.edge", edge);
        }
        refreshInspector();
    }

    /** Uses a library texture: the selected (or a new) sprite emitter, a new overlay, the trigger mask or the look's mask. */
    public void useTexture(String name, String role) {
        if (name == null) {
            return;
        }
        switch (role) {
            case "sprite" -> {
                if (selection.kind() == Selection.Kind.EMITTER) {
                    String p = selection.path();
                    doc.edit(null, r -> {
                        FxDoc.delete(r, p + ".particle");
                        FxDoc.put(r, p + ".sprite", new JsonPrimitive(name));
                    });
                } else {
                    JsonObject emitter = new JsonObject();
                    emitter.addProperty("sprite", name);
                    emitter.addProperty("rate", 1);
                    int index = doc.append("emitters", emitter);
                    select(new Selection(Selection.Kind.EMITTER, index));
                }
            }
            case "overlay" -> addOverlay(name);
            case "trigger" -> {
                doc.edit(null, r -> {
                    FxDoc.put(r, "trigger.type", new JsonPrimitive("mask"));
                    FxDoc.put(r, "trigger.texture", new JsonPrimitive(name));
                });
                select(Selection.EFFECT);
            }
            default -> {
                doc.set("appearance.mask.texture", name);
                select(Selection.APPEARANCE);
            }
        }
        refreshInspector();
        status(EditorUi.tr("status.texture_used", name), true);
    }

    // --- preview ---

    public void togglePlay() {
        preview.setPlaying(!preview.playing());
    }

    /** The interface the preview breaks (remembered between sessions). */
    public void setSample(SampleImage.Kind kind) {
        preview.setSample(kind);
        state.sample = kind.id();
        state.save();
    }

    // --- undo, keys ---

    public void undo() {
        if (doc.undo()) {
            afterHistory();
        }
    }

    public void redo() {
        if (doc.redo()) {
            afterHistory();
        }
    }

    private void afterHistory() {
        select(selection);
        refreshInspector();
    }

    /** Ctrl+Z, Ctrl+Y / Ctrl+Shift+Z, Ctrl+S, Space (play/pause, unless typing). */
    public boolean shortcut(KeyEvent event) {
        int key = event.key();
        UiScreen screen = UiScreen.active();
        if (event.hasControlDown()) {
            if (key == InputConstants.KEY_Z && !event.hasShiftDown()) {
                undo();
                return true;
            }
            if (key == InputConstants.KEY_Y || key == InputConstants.KEY_Z && event.hasShiftDown()) {
                redo();
                return true;
            }
            if (key == InputConstants.KEY_S) {
                save();
                return true;
            }
            return false;
        }
        if (key == InputConstants.KEY_SPACE && screen != null && !screen.hasOverlay()
                && !(screen.focused() instanceof TextField)) {
            togglePlay();
            return true;
        }
        return false;
    }

    // --- files ---

    /** Runs {@code action}, asking first if there are unsaved changes. */
    public void confirmLeave(Runnable action) {
        if (fileId != null ? !doc.dirty() : !doc.changedSinceLoad()) {
            action.run();
            return;
        }
        ConfirmDialog.open(EditorUi.tr("leave.title"), EditorUi.tr("leave.message"), EditorUi.tr("leave.confirm"), true, action);
    }

    public void open(String id) {
        try {
            doc.load(files.read(id));
            fileId = id;
            state.lastEffect = id;
            state.save();
            afterLoad();
            status(EditorUi.tr("status.opened", id), true);
        } catch (IOException | RuntimeException e) {
            status(EditorUi.tr("status.open_failed", id, String.valueOf(e.getMessage())), false);
        }
    }

    /** A new, unsaved effect from a template, under a free id. */
    public void newEffect(String template) {
        JsonObject root = com.google.gson.JsonParser.parseString(Templates.json(template)).getAsJsonObject();
        String id = freeId("empty".equals(template) ? "my_effect" : "my_" + template);
        localizeLabels(root);
        root.addProperty("id", id);
        root.addProperty("name", EditorUi.tr("template." + template).getString());
        doc.load(FxDoc.pretty(root));
        fileId = null;
        afterLoad();
        status(EditorUi.tr("status.new", EditorUi.tr("template." + template)), true);
    }

    public void duplicateEffect() {
        JsonObject root = doc.root().deepCopy();
        String id = freeId(doc.str("id", "effect") + "_copy");
        root.addProperty("id", id);
        root.addProperty("name", EditorUi.tr("copy_of", doc.str("name", id)).getString());
        doc.load(FxDoc.pretty(root));
        fileId = null;
        afterLoad();
        status(EditorUi.tr("status.duplicated", id), true);
    }

    /**
     * A copy of a shipped effect to change: unsaved, under a free id ("<id>_copy"), named "<name> (copy)". Saving it
     * makes it one of the player's effects; the original stays as it is.
     */
    public void cloneFrom(String json, String name) {
        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        String original = root.has("id") ? root.get("id").getAsString() : "";
        String id = freeId(original.isEmpty() ? "my_effect" : original + "_copy");
        localizeLabels(root);
        String descKey = "ui.shatter_gui.effect." + original + ".desc";
        if (Language.getInstance().has(descKey)) {
            root.addProperty("description", Language.getInstance().getOrDefault(descKey));
        }
        root.addProperty("id", id);
        root.addProperty("name", EditorUi.tr("copy_of", name).getString());
        doc.load(FxDoc.pretty(root));
        fileId = null;
        afterLoad();
        status(EditorUi.tr("status.cloned", name), true);
    }

    /**
     * The names of the layers, particles and sounds of a shipped template or effect, in the player's language: they
     * are written in English in the files and translated by {@code ui.shatter_gui.fx.label.<name>}. The copy then
     * belongs to the player, names and all.
     */
    static void localizeLabels(JsonObject root) {
        for (String list : List.of("layers", "emitters", "sounds")) {
            localizeList(root, list);
        }
        if (root.has("reduced") && root.get("reduced").isJsonObject()) {
            localizeList(root.getAsJsonObject("reduced"), "layers");
        }
    }

    private static void localizeList(JsonObject owner, String list) {
        if (!owner.has(list) || !owner.get(list).isJsonArray()) {
            return;
        }
        for (com.google.gson.JsonElement item : owner.getAsJsonArray(list)) {
            if (!item.isJsonObject() || !item.getAsJsonObject().has("name")
                    || !item.getAsJsonObject().get("name").isJsonPrimitive()) {
                continue;
            }
            String key = "ui.shatter_gui.fx.label." + Templates.labelSlug(item.getAsJsonObject().get("name").getAsString());
            if (Language.getInstance().has(key)) {
                item.getAsJsonObject().addProperty("name", Language.getInstance().getOrDefault(key));
            }
        }
    }

    private void afterLoad() {
        // Another effect: the preview must not keep showing the previous one if this one has errors.
        preview.setDef(null);
        selection = Selection.EFFECT;
        timeline.hideCurve();
        preview.seek(0);
        preview.setPlaying(true);
        refreshInspector();
    }

    private String freeId(String wanted) {
        return EffectFiles.freeId(wanted, id -> ShatterEffects.isReserved(id) || files.exists(id)
                || ShatterEffects.dataIds().contains(id));
    }

    /** Saves to {@code effects/<id>.json} (atomically) and reloads the effects. False if it could not. */
    public boolean save() {
        String id = doc.str("id", "");
        if (!FxParser.ID.matcher(id).matches()) {
            status(EditorUi.tr("status.bad_id"), false);
            select(Selection.EFFECT);
            return false;
        }
        if (ShatterEffects.isReserved(id) || !id.equals(fileId) && files.exists(id)) {
            status(EditorUi.tr("check.id_taken", id), false);
            select(Selection.EFFECT);
            return false;
        }
        try {
            files.save(id, doc.json(), fileId);
        } catch (IOException e) {
            status(EditorUi.tr("status.save_failed", String.valueOf(e.getMessage())), false);
            return false;
        }
        fileId = id;
        doc.markSaved();
        state.lastEffect = id;
        state.save();
        FxEffects.reloadAll();
        int errors = errorCount();
        status(errors == 0 ? EditorUi.tr("status.saved", id) : EditorUi.tr("status.saved_errors", id, errors), errors == 0);
        return true;
    }

    public void deleteEffect(String id) {
        try {
            files.delete(id);
        } catch (IOException e) {
            status(EditorUi.tr("status.delete_failed", String.valueOf(e.getMessage())), false);
            return;
        }
        FxEffects.reloadAll();
        if (id.equals(fileId)) {
            fileId = null;
            doc.markSaved();
            List<String> ids = files.ids();
            if (ids.isEmpty()) {
                newEffect("empty");
            } else {
                open(ids.getFirst());
            }
        }
        status(EditorUi.tr("status.deleted", id), true);
    }

    /** Writes {@code exports/<id>.shatterfx} with the effect and its textures; effects without textures also go to the clipboard as a code. */
    public Path exportPackage() {
        String id = doc.str("id", "");
        if (!FxParser.ID.matcher(id).matches()) {
            status(EditorUi.tr("status.bad_id"), false);
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        for (EditorChecks.TextureRef ref : EditorChecks.textureRefs(doc)) {
            if (!ref.name().contains(":")) {
                names.add(ref.name());
            }
        }
        List<String> missing = new ArrayList<>();
        try {
            Path file = files.export(id, doc.json(), names, missing);
            if (names.isEmpty()) {
                String code = FxEffects.codeOf(doc.json());
                if (code != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(code);
                }
            }
            status(missing.isEmpty() ? EditorUi.tr("status.exported", file.getFileName().toString())
                    : EditorUi.tr("status.exported_missing", file.getFileName().toString(), String.join(", ", missing)), missing.isEmpty());
            return file;
        } catch (IOException e) {
            status(EditorUi.tr("status.export_failed", localized(EffectFiles.reason(e))), false);
            return null;
        }
    }

    /** What an import note says, in the player's language. */
    public static Component note(EffectFiles.Note note) {
        return EditorUi.tr("import.note." + note.key(), localized(note.args()));
    }

    /** Arguments of a message with file problems in the player's language ({@link net.shattergui.fx.FxIOException}). */
    static Object[] localized(Object... args) {
        Object[] out = args.clone();
        for (int i = 0; i < out.length; i++) {
            if (out[i] instanceof net.shattergui.fx.FxIOException f) {
                out[i] = EditorUi.tr("io." + f.key(), f.args());
            }
        }
        return out;
    }

    /** Imports a package (checked first) and opens it. */
    public EffectFiles.ImportResult importPackage(Path file) {
        EffectFiles.ImportResult result = files.importPackage(file, id -> ShatterEffects.isBuiltIn(id)
                || ShatterEffects.dataIds().contains(id) && !files.exists(id));
        for (String message : result.messages()) {
            ShatterGuiClient.LOGGER.info("Effect import {}: {}", file.getFileName(), message);
        }
        if (!result.ok()) {
            EffectFiles.Note first = result.firstError();
            status(EditorUi.tr("status.import_failed", first == null ? Component.literal("?") : note(first)), false);
            return result;
        }
        library.rescan();
        FxEffects.reloadAll();
        open(result.id());
        status(EditorUi.tr("status.imported", result.id()), true);
        return result;
    }

    /** Imports an effect code from the clipboard as a new, unsaved effect. */
    public boolean importClipboard() {
        String json = FxEffects.fromCode(Minecraft.getInstance().keyboardHandler.getClipboard());
        if (json == null || FxParser.schemaOf(json) != FxLimits.SCHEMA) {
            status(EditorUi.tr("status.no_code"), false);
            return false;
        }
        FxParser.Result result = FxParser.parse(json);
        if (!result.ok()) {
            status(EditorUi.tr("status.import_failed", result.problems().errorSummary()), false);
            return false;
        }
        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        root.addProperty("id", freeId(result.def().id));
        doc.load(FxDoc.pretty(root));
        fileId = null;
        afterLoad();
        status(EditorUi.tr("status.imported", root.get("id").getAsString()), true);
        return true;
    }

    public void openFolder() {
        try {
            Files.createDirectories(FxEffects.EFFECTS);
            Util.getPlatform().openPath(FxEffects.CONFIG);
        } catch (IOException | RuntimeException e) {
            status(EditorUi.tr("status.folder_failed", String.valueOf(e.getMessage())), false);
        }
    }

    /** Plays the effect as it is now (unsaved) in the world, on a snapshot of the editor. */
    public boolean test() {
        FxParser.Result result = doc.result();
        if (!result.ok()) {
            status(EditorUi.tr("status.test_errors", errorCount()), false);
            return false;
        }
        UiScreen screen = UiScreen.active();
        if (screen == null || Minecraft.getInstance().level == null) {
            status(EditorUi.tr("status.no_world"), false);
            return false;
        }
        releaseTest();
        List<String> warnings = new ArrayList<>();
        testEffect = FxEffects.build(result.def(), warnings);
        if (!PreviewRunner.start(screen, testEffect, values.get())) {
            status(EditorUi.tr("status.no_world"), false);
            return false;
        }
        return true;
    }

    private void releaseTest() {
        if (testEffect != null && !PreviewRunner.running()) {
            FxEffects.release(testEffect);
            testEffect = null;
        }
    }

    // --- host ---

    public void toggleFullscreen() {
        host.toggleFullscreen();
    }

    public void showClassic() {
        host.showClassic();
    }

    public void status(Component text, boolean ok) {
        status = text;
        statusColor = ok ? Theme.SUCCESS : Theme.DANGER;
        statusTime = UiClock.time();
    }

    public Component lastStatus() {
        return status;
    }

    /** The editor goes away (settings closed): textures and the test effect are freed. */
    public void dispose() {
        releaseTest();
        library.releaseAll();
        state.save();
    }

    // --- tour ---

    public void startTour() {
        UiScreen screen = UiScreen.active();
        if (screen == null) {
            return;
        }
        Tour tour = new Tour(List.of(
                new Tour.Step(() -> leftTab == 0 ? layers : textures, "layers"),
                new Tour.Step(() -> previewPane, "preview"),
                new Tour.Step(() -> timeline, "timeline"),
                new Tour.Step(() -> inspector, "inspector")), this::shown, () -> {
            state.tourDone = true;
            state.save();
        });
        screen.openOverlay(tour);
        screen.setFocus(tour);
    }

    /** True while the editor is on screen (laid out this frame or the last). */
    public boolean shown() {
        return UiClock.frameNumber() - arrangedFrame <= 1;
    }

    /** The current tour step (tests), or -1. */
    public int tourStep() {
        UiScreen screen = UiScreen.active();
        if (screen != null) {
            for (UiScreen.Overlay o : screen.overlays()) {
                if (o instanceof Tour t) {
                    return t.step();
                }
            }
        }
        return -1;
    }

    // --- for tests and keyboard users: the panels' dialogs ---

    public void openCatalog() {
        ModuleCatalog.open(this);
    }

    public void openGallery() {
        TemplateGallery.open(this);
    }

    public void openImport() {
        ImportDialog.open(this);
    }

    public void openEffectsMenu() {
        EffectsMenu.open(this, top.effectsButton());
    }

    /** Shows a layer parameter's curve on the timeline (as its "show on the timeline" button). */
    public void showParamCurve(int layer, String param) {
        String path = "layers[" + layer + "]";
        FxDef.Module module = FxDef.Module.byName(doc.str(path + ".module", "stay"));
        if (module == null) {
            return;
        }
        for (FxSchema.Field f : FxSchema.MODULE_PARAMS.get(module)) {
            if (FxSchema.paramName(f).equals(param)) {
                showCurve(path + ".params." + param, f, null, path);
            }
        }
    }

    /** Closes the tour if it is open (counts as seen). */
    public void closeTour() {
        UiScreen screen = UiScreen.active();
        if (screen != null) {
            for (UiScreen.Overlay o : List.copyOf(screen.overlays())) {
                if (o instanceof Tour) {
                    screen.closeOverlay(o);
                }
            }
        }
    }

    /** Moves the open tour on by one step (Enter does the same). */
    public void tourNext() {
        UiScreen screen = UiScreen.active();
        if (screen != null) {
            for (UiScreen.Overlay o : List.copyOf(screen.overlays())) {
                if (o instanceof Tour t) {
                    t.go(t.step() + 1);
                }
            }
        }
    }

    /** Selects the left tab (0 layers, 1 textures). */
    public void showLeftTab(int tab) {
        leftTab = tab;
        leftTabs.select(tab, true);
    }

    /** Chooses a library texture (as a click on its thumbnail). */
    public void chooseTexture(String name) {
        textures.choose(name);
    }

    public void rescanTextures() {
        library.rescan();
        preview.invalidate();
    }

    public EditorPreview previewState() {
        return preview;
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s (%s) %s, %d problems, t=%.1f", doc.str("id", "?"), fileId, selection,
                problems().size(), preview.time());
    }
}
