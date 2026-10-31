package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.ui.ConfirmDialog;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Reveal;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiClock;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * The effects menu under the effect's name: a new effect (the template gallery), the player's effects to open (the
 * current one ticked), duplicate and delete. Arrows move, Enter chooses, Esc or a click outside closes.
 */
final class EffectsMenu extends UiScreen.Overlay {
    private record Item(Icon icon, Component label, boolean danger, Runnable action) {
    }

    private static final int ITEM_H = 14;
    private final Widget anchor;
    private final List<Item> items = new ArrayList<>();
    private final Reveal reveal = new Reveal();
    private int highlight;
    private float scroll;

    private EffectsMenu(FxEditor editor, Widget anchor) {
        this.anchor = anchor;
        items.add(new Item(Icon.PLUS, EditorUi.tr("menu.new"), false, () -> editor.confirmLeave(() -> TemplateGallery.open(editor))));
        for (String id : editor.files.ids()) {
            boolean current = id.equals(editor.fileId());
            items.add(new Item(current ? Icon.CHECK : Icon.STAR, Component.literal(id), false,
                    () -> {
                        if (!current) {
                            editor.confirmLeave(() -> editor.open(id));
                        }
                    }));
        }
        items.add(new Item(Icon.COPY, EditorUi.tr("menu.duplicate"), false, editor::duplicateEffect));
        // The mod's own effects, as examples: a copy opens to change and save as one's own.
        for (String id : ShatterEffects.dataIds()) {
            String json = ShatterEffects.isBundled(id) ? FxEffects.bundledJson(id) : null;
            if (json != null) {
                Component name = ShatterEffects.displayName(id);
                items.add(new Item(Icon.SPARKLES, EditorUi.tr("menu.clone", name), false,
                        () -> editor.confirmLeave(() -> editor.cloneFrom(json, name.getString()))));
            }
        }
        if (editor.fileId() != null) {
            String id = editor.fileId();
            items.add(new Item(Icon.TRASH, EditorUi.tr("menu.delete"), true, () -> ConfirmDialog.open(EditorUi.tr("menu.delete.title"),
                    EditorUi.tr("menu.delete.message", id), EditorUi.tr("menu.delete"), true, () -> editor.deleteEffect(id))));
        }
    }

    /** The menu on screen (open, or animating out), or null. */
    private static EffectsMenu shown(UiScreen screen) {
        for (UiScreen.Overlay o : screen.overlays()) {
            if (o instanceof EffectsMenu menu) {
                return menu;
            }
        }
        return null;
    }

    static void open(FxEditor editor, Widget anchor) {
        UiScreen screen = UiScreen.active();
        EffectsMenu menu = shown(screen);
        if (menu == null) {
            menu = new EffectsMenu(editor, anchor);
            screen.openOverlay(menu);
        }
        // Opened again while closing: the same menu turns round.
        if (menu.reveal.open()) {
            click();
        }
        screen.setFocus(menu);
    }

    /** Its button: opens the menu, or closes it when it is open. */
    static void toggle(FxEditor editor, Widget anchor) {
        EffectsMenu menu = shown(UiScreen.active());
        if (menu != null && menu.reveal.wantsOpen()) {
            menu.dismiss();
        } else {
            open(editor, anchor);
        }
    }

    private void dismiss() {
        if (reveal.close()) {
            closeClick();
        }
        UiScreen screen = UiScreen.active();
        if (screen != null && screen.focused() == this) {
            screen.setFocus(anchor);
        }
    }

    @Override
    public boolean closing() {
        return !reveal.wantsOpen();
    }

    @Override
    public boolean ownsClick(double mx, double my) {
        // A click on its button toggles the menu: not a click outside.
        return anchor.contains(mx, my);
    }

    @Override
    public void outsideClick() {
        dismiss();
    }

    @Override
    public void escape() {
        dismiss();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void place(int screenWidth, int screenHeight) {
        w = Math.max(150, anchor.w);
        h = Math.min(items.size() * ITEM_H + 4, Math.max(40, screenHeight - anchor.y - anchor.h - 8));
        x = Math.clamp(anchor.x, 4, Math.max(4, screenWidth - w - 4));
        y = anchor.y + anchor.h + 2;
    }

    private int maxScroll() {
        return Math.max(0, items.size() * ITEM_H + 4 - h);
    }

    @Override
    protected void draw(Ui ui) {
        if (reveal.tick(UiClock.dt()) == Reveal.State.CLOSED) {
            UiScreen.active().closeOverlay(this);
            return;
        }
        float a = reveal.eased();
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, (1 - a) * -4);
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CONTROL, a);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, Theme.fade(0xFF15161C, a), Theme.fade(Theme.BORDER_STRONG, a));
        if (a < 0.35f) {
            ui.g.pose().popMatrix();
            return;
        }
        ui.pushClip(x + 1, y + 2, w - 2, h - 4);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int iy = Math.round(y + 2 + i * ITEM_H - scroll);
            boolean over = ui.mouseX >= x && ui.mouseX < x + w && ui.mouseY >= iy && ui.mouseY < iy + ITEM_H
                    && ui.mouseY >= y && ui.mouseY < y + h;
            if (over) {
                highlight = i;
            }
            if (i == highlight) {
                Draw.roundRect(ui, x + 2, iy, w - 4, ITEM_H, 3, Theme.accentAlpha(0x38));
            }
            int color = item.danger() ? 0xFFFCA5A5 : Theme.TEXT;
            Draw.icon(ui, item.icon(), x + 6, iy + (ITEM_H - 9) / 2, 1, item.danger() ? Theme.DANGER : Theme.TEXT_DIM);
            ui.textFit(item.label(), x + 19, iy + (ITEM_H - 8) / 2, w - 24, color);
        }
        ui.popClip();
        ui.g.pose().popMatrix();
    }

    @Override
    public Widget hit(double mx, double my) {
        return !closing() && contains(mx, my) ? this : null;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        int index = (int) Math.floor((my - y - 2 + scroll) / ITEM_H);
        if (index >= 0 && index < items.size()) {
            choose(index);
        }
        return true;
    }

    private void choose(int index) {
        if (closing()) {
            return;
        }
        click();
        reveal.close();
        UiScreen.active().setFocus(anchor);
        items.get(index).action().run();
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        scroll = (float) Math.clamp(scroll - amount * ITEM_H, 0, maxScroll());
        return true;
    }

    @Override
    public boolean key(net.minecraft.client.input.KeyEvent event) {
        if (event.isUp() || event.isDown()) {
            highlight = Math.floorMod(highlight + (event.isDown() ? 1 : -1), items.size());
            return true;
        }
        if (event.isConfirmation()) {
            choose(highlight);
            return true;
        }
        return false;
    }
}
