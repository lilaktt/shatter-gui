package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.shattergui.fx.FxLimits;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.nio.file.Files;
import java.util.List;

/**
 * The texture library: thumbnails of the PNGs in the textures folder (a file that cannot be used shows why), the
 * chosen one large — tiled 3×3 if wished, to see how a mask repeats — and what to use it for: the selected emitter's
 * sprite, a new overlay, the trigger mask or the look's mask. "Import" opens the folder (drop PNGs there; the library
 * picks them up by itself), "Refresh" looks at once.
 */
final class TexturePanel extends Container {
    private static final int CELL = 30;
    private final FxEditor editor;
    private final Button importButton;
    private final Button refresh;
    private final Button asSprite;
    private final Button asOverlay;
    private final Button asTrigger;
    private final Button asMask;
    private final Button tiles;
    private String chosen;
    private boolean tiled;
    private float scroll;
    private int gridH;

    TexturePanel(FxEditor editor) {
        this.editor = editor;
        importButton = add(new Button(EditorUi.tr("textures.import"), Icon.IMPORT, Button.Kind.SECONDARY, this::openFolder));
        importButton.leavesScreen(true);
        importButton.tooltip(EditorUi.tr("textures.import"), EditorUi.tr("textures.import.desc",
                FxLimits.MAX_OVERLAY_SIZE, FxLimits.MAX_SPRITE_SIZE, FxLimits.MAX_TEXTURE_BYTES / 1024));
        refresh = add(Button.icon(Icon.REPLAY, EditorUi.tr("textures.refresh"), () -> {
            editor.library.rescan();
            editor.preview.invalidate();
        }));
        asSprite = add(new Button(EditorUi.tr("textures.as_sprite"), Icon.SPARKLES, Button.Kind.GHOST, () -> editor.useTexture(chosen, "sprite")));
        asSprite.tooltip(EditorUi.tr("textures.as_sprite"), EditorUi.tr("textures.as_sprite.desc", FxLimits.MAX_SPRITE_SIZE));
        asOverlay = add(new Button(EditorUi.tr("textures.as_overlay"), Icon.LAYERS, Button.Kind.GHOST, () -> editor.useTexture(chosen, "overlay")));
        asOverlay.tooltip(EditorUi.tr("textures.as_overlay"), EditorUi.tr("textures.as_overlay.desc"));
        asTrigger = add(new Button(EditorUi.tr("textures.as_trigger"), Icon.BOLT, Button.Kind.GHOST, () -> editor.useTexture(chosen, "trigger")));
        asTrigger.tooltip(EditorUi.tr("textures.as_trigger"), EditorUi.tr("textures.as_trigger.desc"));
        asMask = add(new Button(EditorUi.tr("textures.as_mask"), Icon.EYE, Button.Kind.GHOST, () -> editor.useTexture(chosen, "mask")));
        asMask.tooltip(EditorUi.tr("textures.as_mask"), EditorUi.tr("textures.as_mask.desc"));
        tiles = add(new Button(EditorUi.tr("textures.tiles"), Icon.GRID, Button.Kind.GHOST, () -> tiled = !tiled) {
            @Override
            protected void draw(Ui ui) {
                selected(tiled);
                super.draw(ui);
            }
        });
        tiles.tooltip(EditorUi.tr("textures.tiles"), EditorUi.tr("textures.tiles.desc"));
    }

    String chosen() {
        return chosen;
    }

    void choose(String name) {
        chosen = name;
    }

    private void openFolder() {
        try {
            Files.createDirectories(editor.library.dir());
            Util.getPlatform().openPath(editor.library.dir());
        } catch (Exception e) {
            editor.status(EditorUi.tr("status.folder_failed", String.valueOf(e.getMessage())), false);
        }
        editor.library.rescan();
    }

    private int columns() {
        return Math.max(1, (w - 6) / (CELL + 3));
    }

    @Override
    protected void arrange() {
        int buttonH = 16;
        int half = (w - 22) / 2;
        importButton.setBounds(x, y, w - 20, buttonH);
        refresh.setBounds(x + w - 18, y, 18, buttonH);
        // Bottom: the chosen texture and what to do with it.
        TextureLibrary.Entry entry = chosen == null ? null : editor.library.get(chosen);
        boolean show = entry != null && entry.ok();
        int detail = show ? Math.min(h / 2, 64 + 4 * (buttonH + 2)) : 0;
        int by = y + h - detail;
        gridH = Math.max(20, h - buttonH - 4 - detail);
        for (Button b : new Button[]{asSprite, asOverlay, asTrigger, asMask, tiles}) {
            b.visible = show;
        }
        if (show) {
            int py = by + 54;
            asSprite.setBounds(x, py, half + 10, buttonH);
            asOverlay.setBounds(x + half + 12, py, w - half - 12, buttonH);
            asTrigger.setBounds(x, py + buttonH + 2, half + 10, buttonH);
            asMask.setBounds(x + half + 12, py + buttonH + 2, w - half - 12, buttonH);
            tiles.setBounds(x + w - 44, by + 2, 44, 14);
            boolean narrow = half < 70;
            asSprite.text(narrow ? null : EditorUi.tr("textures.as_sprite"));
            asOverlay.text(narrow ? null : EditorUi.tr("textures.as_overlay"));
            asTrigger.text(narrow ? null : EditorUi.tr("textures.as_trigger"));
            asMask.text(narrow ? null : EditorUi.tr("textures.as_mask"));
            tiles.text(w < 110 ? null : EditorUi.tr("textures.tiles"));
        }
    }

    @Override
    protected void draw(Ui ui) {
        editor.library.scanIfDue();
        tooltip(null, null);
        List<TextureLibrary.Entry> entries = editor.library.entries();
        int gy = y + 20;
        int cols = columns();
        int rows = (entries.size() + cols - 1) / cols;
        int contentH = rows * (CELL + 13);
        scroll = Math.clamp(scroll, 0, Math.max(0, contentH - gridH));
        ui.pushClip(x, gy, w, gridH);
        if (entries.isEmpty()) {
            ui.textWrapped(EditorUi.tr("textures.empty"), x + 2, gy + 4, w - 4, Theme.TEXT_FAINT);
        }
        for (int i = 0; i < entries.size(); i++) {
            TextureLibrary.Entry e = entries.get(i);
            int cx = x + 2 + (i % cols) * (CELL + 3);
            int cy = Math.round(gy + (i / cols) * (CELL + 13) - scroll);
            if (cy + CELL + 13 < gy || cy > gy + gridH) {
                continue;
            }
            boolean on = e.name().equals(chosen);
            boolean over = ui.isHovered(this) && ui.mouseX >= cx && ui.mouseX < cx + CELL && ui.mouseY >= cy && ui.mouseY < cy + CELL + 12;
            Draw.roundBox(ui, cx, cy, CELL, CELL, 3, Theme.FIELD, on ? Theme.accent() : over ? Theme.BORDER_STRONG : Theme.BORDER);
            if (e.ok()) {
                EditorUi.checker(ui, cx + 2, cy + 2, CELL - 4, CELL - 4, 3);
                float scale = Math.min((CELL - 4f) / e.width(), (CELL - 4f) / e.height());
                float tw = e.width() * scale;
                float th = e.height() * scale;
                Draw.texture(ui, e.tex().gpu(), cx + (CELL - tw) / 2, cy + (CELL - th) / 2, tw, th, 0xFFFFFFFF);
            } else {
                Draw.icon(ui, Icon.WARNING, cx + (CELL - 9) / 2, cy + (CELL - 9) / 2, 1, Theme.DANGER);
            }
            ui.textSmall(e.name(), cx, cy + CELL + 2, CELL + 2, e.ok() ? (on ? Theme.TEXT : Theme.TEXT_DIM) : 0xFFFCA5A5);
            if (over) {
                tooltip(Component.literal(e.name() + ".png"), e.ok()
                        ? EditorUi.tr("textures.info", e.width(), e.height(), Math.max(1, e.bytes() / 1024))
                        : e.error());
            }
        }
        ui.popClip();
        TextureLibrary.Entry entry = chosen == null ? null : editor.library.get(chosen);
        if (entry != null && entry.ok()) {
            int detail = Math.min(h / 2, 64 + 4 * 18);
            int by = y + h - detail;
            Draw.rect(ui, x, by - 2, w, 1, Theme.BORDER);
            int box = 48;
            EditorUi.checker(ui, x, by + 2, box, box, 4);
            if (tiled) {
                for (int ty = 0; ty < 3; ty++) {
                    for (int tx = 0; tx < 3; tx++) {
                        Draw.texture(ui, entry.tex().gpu(), x + tx * box / 3f, by + 2 + ty * box / 3f, box / 3f, box / 3f, 0xFFFFFFFF);
                    }
                }
            } else {
                Draw.texture(ui, entry.tex().gpu(), x, by + 2, box, box, 0xFFFFFFFF);
            }
            ui.textFit(Component.literal(entry.name()), x + box + 4, by + 4, w - box - 50, Theme.TEXT);
            ui.textSmall(EditorUi.tr("textures.info", entry.width(), entry.height(), Math.max(1, entry.bytes() / 1024)).getString(),
                    x + box + 4, by + 15, w - box - 4, Theme.TEXT_DIM);
            if (entry.width() > FxLimits.MAX_SPRITE_SIZE || entry.height() > FxLimits.MAX_SPRITE_SIZE) {
                ui.textSmall(EditorUi.tr("textures.no_sprite", FxLimits.MAX_SPRITE_SIZE).getString(), x + box + 4, by + 24,
                        w - box - 4, Theme.WARNING);
            }
            asSprite.enabled = entry.width() <= FxLimits.MAX_SPRITE_SIZE && entry.height() <= FxLimits.MAX_SPRITE_SIZE;
        }
        super.draw(ui);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        int gy = y + 20;
        if (button != 0 || my < gy || my > gy + gridH) {
            return false;
        }
        List<TextureLibrary.Entry> entries = editor.library.entries();
        int cols = columns();
        int col = (int) ((mx - x - 2) / (CELL + 3));
        int row = (int) ((my - gy + scroll) / (CELL + 13));
        int index = row * cols + col;
        if (col >= 0 && col < cols && index >= 0 && index < entries.size()) {
            chosen = entries.get(index).name();
            if (!entries.get(index).ok()) {
                editor.status(Component.literal(entries.get(index).name() + ".png: ").append(entries.get(index).error()), false);
            }
        }
        return true;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        scroll = (float) Math.max(0, scroll - amount * 20);
        return true;
    }
}
