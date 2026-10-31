package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;
import net.shattergui.ui.Button;
import net.shattergui.ui.Column;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Section;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.Widget;

import java.util.List;

/**
 * The left panel: the effect's parts as a list — its settings, its look and the edge flight, then the layers,
 * particle emitters and sounds. Each item can be shown or hidden (the eye: hidden items are kept but not played),
 * renamed (double click), dragged to another place in its group, duplicated and removed; problems show as a dot.
 * "Add" opens the catalogue of modules.
 */
final class LayerPanel extends Container {
    private static final int ROW_H = 16;
    private final FxEditor editor;
    private final Column list = new Column(0, 6);
    private final ScrollView scroll = new ScrollView(list);
    private final Button addButton;
    private String shape = "";
    /** The row being dragged and where it would go (index in its group), or null. */
    private ItemRow dragged;
    private int dropIndex = -1;

    LayerPanel(FxEditor editor) {
        this.editor = editor;
        add(scroll);
        addButton = add(new Button(EditorUi.tr("add"), Icon.PLUS, Button.Kind.PRIMARY, () -> ModuleCatalog.open(editor)));
        addButton.tooltip(EditorUi.tr("add"), EditorUi.tr("add.desc"));
    }

    /** Rebuilds the rows when the lists change size. */
    private void sync() {
        String now = editor.doc.size("layers") + "/" + editor.doc.size("emitters") + "/" + editor.doc.size("sounds");
        if (now.equals(shape)) {
            return;
        }
        shape = now;
        list.clear();
        Section parts = list.add(new Section("layers.parts", Icon.GRID, EditorUi.tr("left.parts"), false, 1));
        parts.add(new SectionRow(Selection.EFFECT, Icon.GRID, EditorUi.tr("part.effect")));
        parts.add(new SectionRow(Selection.APPEARANCE, Icon.PALETTE, EditorUi.tr("part.appearance")));
        parts.add(new SectionRow(Selection.EDGE, Icon.EDGE, EditorUi.tr("part.edge")));
        group(Selection.Kind.LAYER, Icon.LAYERS, "group.layers", FxLimits.MAX_LAYERS);
        group(Selection.Kind.EMITTER, Icon.SPARKLES, "group.emitters", FxLimits.MAX_EMITTERS);
        group(Selection.Kind.SOUND, Icon.NOTE, "group.sounds", FxLimits.MAX_SOUNDS);
    }

    /** A card per group: its name, how many of the maximum, its rows (or a hint when empty). */
    private void group(Selection.Kind kind, Icon icon, String key, int max) {
        String listName = Selection.listOf(kind);
        int count = editor.doc.size(listName);
        Section section = list.add(new Section("layers." + listName, icon, EditorUi.tr(key + ".title"), false, 1));
        section.badge(() -> net.minecraft.network.chat.Component.literal(editor.doc.size(listName) + "/" + max), Theme.TEXT_FAINT);
        if (count == 0) {
            section.add(new net.shattergui.ui.Hint(EditorUi.tr(key + ".empty")));
        }
        for (int i = 0; i < count; i++) {
            section.add(new ItemRow(kind, i));
        }
    }

    @Override
    protected void arrange() {
        sync();
        int buttonH = 18;
        scroll.setBounds(x, y, w, h - buttonH - 4);
        addButton.setBounds(x + 2, y + h - buttonH, w - 4, buttonH);
        addButton.enabled = editor.doc.size("layers") < FxLimits.MAX_LAYERS || editor.doc.size("emitters") < FxLimits.MAX_EMITTERS
                || editor.doc.size("sounds") < FxLimits.MAX_SOUNDS;
    }

    @Override
    protected void draw(Ui ui) {
        sync();
        super.draw(ui);
    }

    // --- names ---

    /** What a list item is called: its own name, else what it is (module, particle, sound). */
    static Component itemName(FxDoc doc, Selection.Kind kind, int index) {
        String path = Selection.listOf(kind) + "[" + index + "]";
        String own = doc.str(path + ".name", "").trim();
        if (!own.isEmpty()) {
            return Component.literal(own);
        }
        return switch (kind) {
            case LAYER -> {
                FxDef.Module module = FxDef.Module.byName(doc.str(path + ".module", "stay"));
                yield module == null ? Component.literal(doc.str(path + ".module", "?")) : EditorUi.moduleName(module);
            }
            case EMITTER -> {
                String particle = doc.str(path + ".particle", null);
                String sprite = doc.str(path + ".sprite", null);
                yield particle != null ? EditorUi.tr("emitter.vanilla_name", shortId(particle))
                        : EditorUi.tr("emitter.sprite_name", sprite == null ? "?" : sprite);
            }
            default -> Component.literal(shortId(doc.str(path + ".sound", "?")));
        };
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    static Icon itemIcon(FxDoc doc, Selection.Kind kind, int index) {
        return switch (kind) {
            case LAYER -> {
                FxDef.Module module = FxDef.Module.byName(doc.str("layers[" + index + "].module", "stay"));
                yield module == null ? Icon.WARNING : EditorUi.icon(module);
            }
            case EMITTER -> doc.has("emitters[" + index + "].sprite") ? Icon.IMAGE : Icon.SPARKLES;
            default -> Icon.NOTE;
        };
    }

    static int itemColor(FxDoc doc, Selection.Kind kind, int index) {
        return switch (kind) {
            case LAYER -> {
                FxDef.Module module = FxDef.Module.byName(doc.str("layers[" + index + "].module", "stay"));
                yield module == null ? Theme.DANGER : EditorUi.color(module);
            }
            case EMITTER -> EditorUi.EMITTER_COLOR;
            default -> EditorUi.SOUND_COLOR;
        };
    }

    // --- rows ---

    private static void problemDot(Ui ui, List<FxProblems.Problem> problems, int x, int y) {
        if (problems.isEmpty()) {
            return;
        }
        boolean error = problems.stream().anyMatch(FxProblems.Problem::error);
        Draw.circle(ui, x, y, 2, error ? Theme.DANGER : Theme.WARNING);
    }

    private final class Header extends Widget {
        private final java.util.function.Supplier<Component> text;

        Header(java.util.function.Supplier<Component> text) {
            this.text = text;
            this.h = 14;
        }

        @Override
        protected void draw(Ui ui) {
            ui.textSmall(text.get().getString().toUpperCase(java.util.Locale.ROOT), x + 3, y + 6, w - 6, Theme.TEXT_FAINT);
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    private static final class Hint extends Widget {
        private final Component text;

        Hint(Component text) {
            this.text = text;
            this.h = 10;
        }

        @Override
        protected void draw(Ui ui) {
            ui.textSmall(text.getString(), x + 6, y + 2, w - 8, Theme.TEXT_FAINT);
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    /** The effect, its look, the edge flight. */
    private final class SectionRow extends Widget {
        private final Selection selection;
        private final Icon icon;
        private final Component title;
        private final Component shortTitle;

        SectionRow(Selection selection, Icon icon, Component title) {
            this.selection = selection;
            this.icon = icon;
            this.title = title;
            this.shortTitle = selection.kind() == Selection.Kind.EDGE ? EditorUi.tr("part.edge.short") : null;
            this.h = ROW_H;
            tooltip(title, EditorUi.tr("part." + selection.kind().name().toLowerCase(java.util.Locale.ROOT) + ".desc"));
        }

        @Override
        public boolean focusable() {
            return true;
        }

        private boolean edgeOn() {
            return editor.doc.has("modifiers.edge");
        }

        @Override
        protected void draw(Ui ui) {
            boolean on = selection.equals(editor.selection());
            int bg = on ? Theme.accentSurface() : Theme.fade(Theme.RAISED, hover.value() * 0.8f);
            Draw.roundRect(ui, x, y, w, h, 3, bg);
            if (on) {
                Draw.rect(ui, x, y + 3, 2, h - 6, Theme.accent());
            }
            boolean dim = selection.kind() == Selection.Kind.EDGE && !edgeOn();
            int color = dim ? Theme.TEXT_FAINT : on ? Theme.TEXT : Theme.TEXT_DIM;
            Draw.icon(ui, icon, x + 5, y + (h - 9) / 2, 1, on ? Theme.accentHover() : color);
            // The full name if it fits, else its short form (the tooltip always has the full one).
            Component shown = ui.font.width(title) <= w - 34 || shortTitle == null ? title : shortTitle;
            ui.textFit(shown, x + 17, y + (h - 8) / 2, w - 34, color);
            problemDot(ui, editor.problemsAt(selection.path(), selection.kind() == Selection.Kind.EFFECT), x + w - 20, y + h / 2);
            if (selection.kind() == Selection.Kind.EDGE) {
                Draw.icon(ui, edgeOn() ? Icon.EYE : Icon.EYE_OFF, x + w - 12, y + (h - 9) / 2, 1,
                        edgeOn() ? Theme.TEXT_DIM : Theme.TEXT_FAINT);
            }
            if (focus.value() > 0.05f) {
                Draw.roundOutline(ui, x, y, w, h, 3, Theme.accentAlpha(Math.round(160 * focus.value())));
            }
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            if (selection.kind() == Selection.Kind.EDGE && mx >= x + w - 14) {
                editor.toggleEdge();
            }
            editor.select(selection);
            return true;
        }

        @Override
        public boolean key(net.minecraft.client.input.KeyEvent event) {
            if (event.isSelection()) {
                editor.select(selection);
                return true;
            }
            return false;
        }
    }

    /** A layer, emitter or sound. */
    private final class ItemRow extends Container {
        private final Selection selection;
        private final TextField rename = new TextField(FxParser.MAX_LABEL);
        private boolean renaming;
        private double pressY;
        private boolean pressed;

        ItemRow(Selection.Kind kind, int index) {
            this.selection = new Selection(kind, index);
            this.h = ROW_H;
            rename.visible = false;
            rename.onEnter(() -> finishRename(true));
            rename.onEscape(() -> finishRename(false));
            add(rename);
        }

        private String path() {
            return selection.path();
        }

        private boolean enabled() {
            return editor.doc.bool(path() + ".enabled", true);
        }

        @Override
        public boolean focusable() {
            return !renaming;
        }

        @Override
        protected int measure(int width) {
            rename.setBounds(x + 16, y, width - 50, h);
            return ROW_H;
        }

        @Override
        protected void draw(Ui ui) {
            if (renaming && screen() != null && screen().focused() != rename) {
                finishRename(true);
            }
            boolean on = selection.equals(editor.selection());
            boolean over = ui.isWithin(this);
            int bg = on ? Theme.accentSurface() : Theme.fade(Theme.RAISED, over ? 0.8f : 0f);
            if (dragged == this) {
                bg = Theme.accentAlpha(0x60);
            }
            Draw.roundRect(ui, x, y, w, h, 3, bg);
            int stripe = itemColor(editor.doc, selection.kind(), selection.index());
            Draw.rect(ui, x + 1, y + 3, 2, h - 6, enabled() ? stripe : Theme.fade(stripe, 0.35f));
            int textColor = !enabled() ? Theme.TEXT_FAINT : on ? Theme.TEXT : Theme.TEXT_DIM;
            if (over && !renaming) {
                Draw.icon(ui, Icon.GRIP, x + 3, y + (h - 9) / 2, 1, Theme.TEXT_FAINT);
            } else {
                Draw.icon(ui, itemIcon(editor.doc, selection.kind(), selection.index()), x + 5, y + (h - 9) / 2, 1,
                        enabled() ? Theme.mix(stripe, 0xFFFFFFFF, 0.3f) : Theme.TEXT_FAINT);
            }
            int right = x + w - 12;
            if (!renaming) {
                int textRight = over ? x + w - 40 : x + w - 22;
                ui.textFit(itemName(editor.doc, selection.kind(), selection.index()), x + 17, y + (h - 8) / 2, textRight - x - 17,
                        textColor);
            }
            problemDot(ui, editor.problemsAt(path(), false), x + w - 20, y + h / 2);
            Draw.icon(ui, enabled() ? Icon.EYE : Icon.EYE_OFF, right, y + (h - 9) / 2, 1,
                    enabled() ? (over && ui.mouseX >= right - 1 ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_FAINT);
            if (over && !renaming) {
                Draw.icon(ui, Icon.COPY, right - 12, y + (h - 9) / 2, 1,
                        ui.mouseX >= right - 13 && ui.mouseX < right - 1 ? Theme.TEXT : Theme.TEXT_FAINT);
                Draw.icon(ui, Icon.TRASH, right - 24, y + (h - 9) / 2, 1,
                        ui.mouseX >= right - 25 && ui.mouseX < right - 13 ? Theme.DANGER : Theme.TEXT_FAINT);
            }
            if (dragged != null && dragged != this && dragged.selection.kind() == selection.kind()) {
                int target = dropIndex;
                int me = selection.index();
                if (target == me && dragged.selection.index() > me) {
                    Draw.rect(ui, x, y - 1, w, 2, Theme.accent());
                } else if (target == me && dragged.selection.index() < me) {
                    Draw.rect(ui, x, y + h - 1, w, 2, Theme.accent());
                }
            }
            if (focus.value() > 0.05f) {
                Draw.roundOutline(ui, x, y, w, h, 3, Theme.accentAlpha(Math.round(160 * focus.value())));
            }
            super.draw(ui);
            if (renaming) {
                tooltip(null, null);
            } else {
                tooltip(itemName(editor.doc, selection.kind(), selection.index()), EditorUi.tr("row.tooltip"));
            }
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            if (renaming) {
                return false;
            }
            int right = x + w - 12;
            boolean over = true;
            if (button == 0 && mx >= right - 1) {
                boolean shown = enabled();
                String field = path() + ".enabled";
                editor.doc.edit(null, r -> {
                    if (shown) {
                        FxDoc.put(r, field, new com.google.gson.JsonPrimitive(false));
                    } else {
                        FxDoc.delete(r, field);
                    }
                });
                return true;
            }
            if (button == 0 && over && mx >= right - 13 && mx < right - 1) {
                editor.duplicate(selection);
                return true;
            }
            if (button == 0 && over && mx >= right - 25 && mx < right - 13) {
                editor.removeItem(selection);
                return true;
            }
            if (button == 1) {
                startRename();
                return true;
            }
            editor.select(selection);
            pressed = true;
            pressY = my;
            return true;
        }

        @Override
        public void mouseDrag(double mx, double my) {
            if (!pressed) {
                return;
            }
            if (dragged == null && Math.abs(my - pressY) > 3) {
                dragged = this;
            }
            if (dragged == this) {
                int count = editor.doc.size(selection.list());
                int index = selection.index() + (int) Math.round((my - pressY) / (ROW_H + 1));
                dropIndex = Math.clamp(index, 0, Math.max(0, count - 1));
            }
        }

        @Override
        public void mouseUp(double mx, double my, int button) {
            pressed = false;
            if (dragged == this) {
                int to = dropIndex;
                dragged = null;
                dropIndex = -1;
                if (to >= 0 && to != selection.index()) {
                    editor.moveItem(selection, to);
                }
            }
        }

        @Override
        public boolean doubleClicked(double mx, double my) {
            if (mx >= x + w - 40) {
                return false;
            }
            startRename();
            return true;
        }

        private void startRename() {
            renaming = true;
            rename.visible = true;
            rename.value(editor.doc.str(path() + ".name", itemName(editor.doc, selection.kind(), selection.index()).getString()));
            rename.selectAll();
            screen().setFocus(rename);
            rename.selectAll();
        }

        private void finishRename(boolean apply) {
            if (!renaming) {
                return;
            }
            renaming = false;
            rename.visible = false;
            if (apply) {
                String name = rename.value().trim();
                if (name.isEmpty()) {
                    editor.doc.remove(path() + ".name");
                } else {
                    editor.doc.set(path() + ".name", name);
                }
                editor.doc.breakMerge();
            }
            if (screen() != null && screen().focused() == rename) {
                screen().setFocus(this);
            }
        }

        @Override
        public boolean key(net.minecraft.client.input.KeyEvent event) {
            int key = event.key();
            if (event.isSelection()) {
                editor.select(selection);
                return true;
            }
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_F2) {
                startRename();
                return true;
            }
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) {
                editor.removeItem(selection);
                return true;
            }
            return false;
        }
    }
}
