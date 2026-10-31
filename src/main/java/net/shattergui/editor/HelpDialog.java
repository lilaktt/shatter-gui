package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.ui.Anim;
import net.shattergui.ui.Button;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * "Help": short pages about the editor's ideas — layers, targets, time and curves, the timeline, textures, particles,
 * limits, sharing an effect. Page buttons on the left, the page on the right (the wheel scrolls a long one). Opened
 * from the top bar or from a card's "?" at that card's page.
 */
public final class HelpDialog extends UiScreen.Overlay {
    /** The pages, in order; each has ui.shatter_gui.fx.help.<id>.title and .text. */
    public static final List<String> PAGES = List.of("layers", "targets", "timing", "timeline", "textures", "particles",
            "limits", "sharing");

    private final List<Button> pageButtons = new ArrayList<>();
    private final Button close;
    private final Anim appear = new Anim(0f, Theme.SPEED_APPEAR + 3f);
    private int page;
    private float scroll;
    private int textH;

    private HelpDialog(int page) {
        this.page = page;
        for (int i = 0; i < PAGES.size(); i++) {
            int index = i;
            String id = PAGES.get(i);
            Button b = add(new Button(title(id), null, Button.Kind.GHOST, () -> {
                this.page = index;
                scroll = 0;
            }) {
                @Override
                protected void draw(Ui ui) {
                    selected(HelpDialog.this.page == index);
                    super.draw(ui);
                }
            });
            b.tooltip(title(id), EditorUi.tr("help.page.desc"));
            pageButtons.add(b);
        }
        close = add(new Button(Component.translatable("gui.done"), null, Button.Kind.PRIMARY,
                () -> UiScreen.active().closeOverlay(this)));
        close.tooltip(Component.translatable("gui.done"), EditorUi.tr("help.close.desc"));
    }

    private static final String PAGE_KEY = "ui.shatter_gui.fx.help.";

    private static Component title(String id) {
        return Component.translatable(PAGE_KEY + id + ".title");
    }

    private static Component text(String id) {
        return Component.translatable(PAGE_KEY + id + ".text");
    }

    /** Opens the help at a page ("layers", …; null or unknown: the first). */
    public static void open(String pageId) {
        int index = Math.max(0, pageId == null ? 0 : PAGES.indexOf(pageId));
        HelpDialog dialog = new HelpDialog(index);
        UiScreen.active().openOverlay(dialog);
        UiScreen.active().setFocus(dialog.pageButtons.get(index));
    }

    /** A card's "?": hover text (the page's text) and a click that opens the page. */
    public static void attach(net.shattergui.ui.Section section, String pageId) {
        section.help(title(pageId), text(pageId), () -> open(pageId));
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public void outsideClick() {
        UiScreen.active().closeOverlay(this);
    }

    private int listW() {
        return Math.min(110, w / 3);
    }

    @Override
    public void place(int screenWidth, int screenHeight) {
        w = Math.min(440, screenWidth - 16);
        h = Math.min(290, screenHeight - 16);
        x = (screenWidth - w) / 2;
        y = (screenHeight - h) / 2;
        int by = y + 26;
        for (Button b : pageButtons) {
            b.setBounds(x + 8, by, listW(), 16);
            by += 18;
        }
        int cw = Math.max(64, close.naturalWidth());
        close.setBounds(x + w - 8 - cw, y + h - 24, cw, 18);
    }

    private int textX() {
        return x + 8 + listW() + 10;
    }

    private int textTop() {
        return y + 26 + Theme.LINE + 6;
    }

    private int textBottom() {
        return y + h - 30;
    }

    @Override
    protected void draw(Ui ui) {
        float a = appear.target(1f).tick();
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, (1 - a) * 6);
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CARD, 1f);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, 0xFF17181F, Theme.BORDER_STRONG);
        Draw.roundRect(ui, x, y, w, 3, 1, Theme.accent());
        Draw.icon(ui, Icon.HELP, x + 10, y + 9, 1, Theme.accentHover());
        ui.text(EditorUi.tr("help.title"), x + 23, y + 10, Theme.TEXT);
        for (Button b : pageButtons) {
            b.render(ui);
        }
        String id = PAGES.get(page);
        int tx = textX();
        int tw = x + w - 10 - tx;
        ui.text(title(id), tx, y + 26, Theme.TEXT);
        Draw.rect(ui, tx, y + 26 + Theme.LINE + 2, tw, 1, Theme.BORDER);
        ui.pushClip(tx, textTop(), tw, textBottom() - textTop());
        textH = ui.textWrapped(text(id), tx, Math.round(textTop() - scroll), tw, Theme.TEXT_DIM);
        ui.popClip();
        close.render(ui);
        ui.g.pose().popMatrix();
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        float max = Math.max(0, textH - (textBottom() - textTop()));
        scroll = (float) Math.clamp(scroll - amount * Theme.LINE * 2, 0, max);
        return true;
    }

    @Override
    public boolean focusable() {
        return false;
    }
}
