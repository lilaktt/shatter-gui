package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.List;

/**
 * The editor's top bar: the effects menu (shows the effect's id), its name, Save (with a dot while unsaved),
 * Undo/Redo, then Test in the world, Export, Import, Open folder, the classic (format 1) effects, Help and full screen.
 * When narrow, the secondary buttons show icons only (their tooltips name them).
 */
final class TopBar extends Container {
    private final FxEditor editor;
    private final Button effects;
    private final TextField name = new TextField(48);
    private final Button save;
    private final Button undo;
    private final Button redo;
    private final Button test;
    private final Button export;
    private final Button importButton;
    private final Button folder;
    private final Button classic;
    private final Button fullscreen;
    private final Button help;

    TopBar(FxEditor editor) {
        this.editor = editor;
        effects = add(new Button(Component.literal("…"), Icon.CHEVRON_DOWN, Button.Kind.SECONDARY, () -> EffectsMenu.toggle(editor, this.effects())));
        effects.tooltip(EditorUi.tr("top.effects"), EditorUi.tr("top.effects.desc"));
        name.hint(EditorUi.tr("field.name.hint"));
        name.onChange(v -> editor.doc.set("name", v));
        name.tooltip(EditorUi.tr("field.name"), EditorUi.tr("field.name.desc"));
        add(name);
        save = add(new Button(EditorUi.tr("top.save"), Icon.SAVE, Button.Kind.PRIMARY, editor::save) {
            @Override
            protected void draw(Ui ui) {
                super.draw(ui);
                if (editor.doc.dirty() || editor.fileId() == null) {
                    Draw.circle(ui, x + w - 4, y + 4, 2, Theme.WARNING);
                }
            }
        });
        save.tooltip(EditorUi.tr("top.save"), EditorUi.tr("top.save.desc"));
        undo = add(Button.icon(Icon.UNDO, EditorUi.tr("top.undo"), editor::undo));
        undo.tooltip(EditorUi.tr("top.undo"), EditorUi.tr("top.undo.desc"));
        redo = add(Button.icon(Icon.REDO, EditorUi.tr("top.redo"), editor::redo));
        redo.tooltip(EditorUi.tr("top.redo"), EditorUi.tr("top.redo.desc"));
        test = add(new Button(EditorUi.tr("top.test"), Icon.PLAY, Button.Kind.SECONDARY, editor::test));
        test.tooltip(EditorUi.tr("top.test"), EditorUi.tr("top.test.desc"));
        test.leavesScreen(true);
        export = add(new Button(EditorUi.tr("top.export"), Icon.EXPORT, Button.Kind.GHOST, editor::exportPackage));
        export.tooltip(EditorUi.tr("top.export"), EditorUi.tr("top.export.desc"));
        importButton = add(new Button(EditorUi.tr("top.import"), Icon.IMPORT, Button.Kind.GHOST, () -> ImportDialog.open(editor)));
        importButton.tooltip(EditorUi.tr("top.import"), EditorUi.tr("top.import.desc"));
        folder = add(new Button(EditorUi.tr("top.folder"), Icon.FOLDER, Button.Kind.GHOST, editor::openFolder));
        folder.tooltip(EditorUi.tr("top.folder"), EditorUi.tr("top.folder.desc"));
        folder.leavesScreen(true);
        classic = add(new Button(EditorUi.tr("top.classic"), Icon.STAR, Button.Kind.GHOST, editor::showClassic));
        classic.tooltip(EditorUi.tr("top.classic"), EditorUi.tr("top.classic.desc"));
        fullscreen = add(Button.icon(Icon.EXPAND, EditorUi.tr("top.fullscreen"), editor::toggleFullscreen));
        fullscreen.leavesScreen(true);
        help = add(Button.icon(Icon.HELP, EditorUi.tr("help.button"), () -> HelpDialog.open(null)));
        help.tooltip(EditorUi.tr("help.button"), EditorUi.tr("help.button.desc"));
    }

    private Button effects() {
        return effects;
    }

    Button effectsButton() {
        return effects;
    }

    @Override
    protected void arrange() {
        int bh = Theme.CONTROL_HEIGHT;
        int by = y + 1;
        classic.visible = editor.host().canShowClassic();
        fullscreen.tooltip(EditorUi.tr(editor.host().fullscreen() ? "top.window" : "top.fullscreen"), EditorUi.tr("top.fullscreen.desc"));
        String id = editor.doc.str("id", "?");
        effects.text(Component.literal(id.isEmpty() ? "?" : id));
        undo.enabled = editor.doc.canUndo();
        redo.enabled = editor.doc.canRedo();
        List<Button> secondary = List.of(test, export, importButton, folder, classic);
        // Full labels when they fit, else icons only.
        int fixed = 18 * 4 + 6 * 2 + 3;
        int labelled = 0;
        for (Button b : secondary) {
            if (b.visible) {
                b.text(EditorUi.tr(key(b)));
                labelled += b.naturalWidth() + 3;
            }
        }
        int effectsW = Math.clamp(w / 6, 60, 120);
        int nameMin = 70;
        int saveW = save.naturalWidth();
        boolean icons = effectsW + nameMin + saveW + fixed + labelled + 20 > w;
        boolean saveIcon = icons && w < 420;
        save.text(saveIcon ? null : EditorUi.tr("top.save"));
        saveW = saveIcon ? 18 : save.naturalWidth();
        int cx = x + w;
        cx -= 18;
        fullscreen.setBounds(cx, by, 18, bh);
        cx -= 21;
        help.setBounds(cx, by, 18, bh);
        for (int i = secondary.size() - 1; i >= 0; i--) {
            Button b = secondary.get(i);
            if (!b.visible) {
                continue;
            }
            if (icons) {
                b.text(null);
            }
            int bw = icons ? 18 : b.naturalWidth();
            cx -= bw + 3;
            b.setBounds(cx, by, bw, bh);
        }
        cx -= 6;
        cx -= 18;
        redo.setBounds(cx, by, 18, bh);
        cx -= 21;
        undo.setBounds(cx, by, 18, bh);
        cx -= saveW + 4;
        save.setBounds(cx, by, saveW, bh);
        effects.setBounds(x, by, effectsW, bh);
        int nameX = x + effectsW + 4;
        name.setBounds(nameX, by, Math.max(20, cx - 6 - nameX), bh);
        // Show the effect's name unless the player is typing in the field.
        if (net.shattergui.ui.UiScreen.active() == null || net.shattergui.ui.UiScreen.active().focused() != name) {
            String docName = editor.doc.str("name", "");
            if (!docName.equals(name.value())) {
                name.value(docName);
            }
        }
    }

    private String key(Button b) {
        if (b == test) {
            return "top.test";
        }
        if (b == export) {
            return "top.export";
        }
        if (b == importButton) {
            return "top.import";
        }
        if (b == folder) {
            return "top.folder";
        }
        return "top.classic";
    }
}
