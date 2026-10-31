package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.shattergui.ui.Anim;
import net.shattergui.ui.Button;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * "Import": the packages found in the exports and packages folders (newest first) — a click imports one, checked
 * first, nothing written if it is broken — or an effect code from the clipboard. "Open folder" shows where to drop
 * a .shatterfx file.
 */
final class ImportDialog extends UiScreen.Overlay {
    private static final int ROW = 14;
    private final FxEditor editor;
    private final List<Path> files;
    private final Button clipboard;
    private final Button folder;
    private final Button cancel;
    private final Anim appear = new Anim(0f, Theme.SPEED_APPEAR + 3f);
    private float scroll;

    private ImportDialog(FxEditor editor) {
        this.editor = editor;
        this.files = editor.files.importable();
        clipboard = add(new Button(EditorUi.tr("import.clipboard"), Icon.IMPORT, Button.Kind.SECONDARY, () -> {
            UiScreen.active().closeOverlay(this);
            editor.importClipboard();
        }));
        clipboard.tooltip(EditorUi.tr("import.clipboard"), EditorUi.tr("import.clipboard.desc"));
        folder = add(new Button(EditorUi.tr("import.folder"), Icon.FOLDER, Button.Kind.GHOST, () -> {
            try {
                Files.createDirectories(editor.files.exports);
                Util.getPlatform().openPath(editor.files.exports);
            } catch (Exception e) {
                editor.status(EditorUi.tr("status.folder_failed", String.valueOf(e.getMessage())), false);
            }
        }));
        folder.leavesScreen(true);
        cancel = add(new Button(Component.translatable("gui.cancel"), null, Button.Kind.SECONDARY,
                () -> UiScreen.active().closeOverlay(this)));
    }

    static void open(FxEditor editor) {
        UiScreen.active().openOverlay(new ImportDialog(editor));
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public void outsideClick() {
        UiScreen.active().closeOverlay(this);
    }

    @Override
    public void place(int screenWidth, int screenHeight) {
        w = Math.min(320, screenWidth - 16);
        h = Math.min(240, screenHeight - 16);
        x = (screenWidth - w) / 2;
        y = (screenHeight - h) / 2;
        int by = y + h - 24;
        int cw = Math.max(64, cancel.naturalWidth());
        cancel.setBounds(x + w - 8 - cw, by, cw, 18);
        int clipW = Math.min(clipboard.naturalWidth(), w / 2 - 12);
        clipboard.setBounds(x + 8, by, clipW, 18);
        folder.setBounds(x + 8 + clipW + 4, by, Math.max(18, cancel.x - 4 - (x + 12 + clipW)), 18);
    }

    private int listY() {
        return y + 36;
    }

    private int listH() {
        return Math.max(ROW, h - 36 - 30);
    }

    @Override
    protected void draw(Ui ui) {
        float a = appear.target(1f).tick();
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, (1 - a) * 6);
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CARD, 1f);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, 0xFF17181F, Theme.BORDER_STRONG);
        Draw.roundRect(ui, x, y, w, 3, 1, Theme.accent());
        ui.text(EditorUi.tr("import.title"), x + 10, y + 9, Theme.TEXT);
        ui.textSmall(EditorUi.tr("import.desc").getString(), x + 10, y + 22, w - 20, Theme.TEXT_DIM);
        Draw.roundBox(ui, x + 8, listY(), w - 16, listH(), 3, Theme.FIELD, Theme.BORDER);
        ui.pushClip(x + 9, listY() + 1, w - 18, listH() - 2);
        if (files.isEmpty()) {
            ui.textWrapped(EditorUi.tr("import.none"), x + 14, listY() + 5, w - 28, Theme.TEXT_FAINT);
        }
        for (int i = 0; i < files.size(); i++) {
            int ry = Math.round(listY() + 2 + i * ROW - scroll);
            Path file = files.get(i);
            boolean over = ui.mouseX >= x + 8 && ui.mouseX < x + w - 8 && ui.mouseY >= ry && ui.mouseY < ry + ROW
                    && ui.mouseY >= listY() && ui.mouseY < listY() + listH();
            if (over) {
                Draw.rect(ui, x + 10, ry, w - 20, ROW, Theme.accentAlpha(0x38));
            }
            Draw.icon(ui, Icon.IMPORT, x + 13, ry + 2, 1, Theme.TEXT_DIM);
            ui.textFit(Component.literal(file.getFileName().toString()), x + 26, ry + 3, w - 110, Theme.TEXT);
            ui.textSmall(file.getParent().getFileName().toString(), x + w - 80, ry + 4, 70, Theme.TEXT_FAINT);
        }
        ui.popClip();
        super.draw(ui);
        ui.g.pose().popMatrix();
    }

    @Override
    public Widget hit(double mx, double my) {
        Widget child = super.hit(mx, my);
        return child;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0 || my < listY() || my > listY() + listH()) {
            return true;
        }
        int index = (int) Math.floor((my - listY() - 2 + scroll) / ROW);
        if (index >= 0 && index < files.size()) {
            Path file = files.get(index);
            UiScreen.active().closeOverlay(this);
            editor.confirmLeave(() -> editor.importPackage(file));
        }
        return true;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        scroll = (float) Math.clamp(scroll - amount * ROW, 0, Math.max(0, files.size() * ROW + 4 - listH()));
        return true;
    }
}
