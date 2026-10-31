package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A drop-down list: shows the chosen option (with its thumbnail, if any) and opens a list above everything else
 * ({@link Popup}) on click, Enter or Space. In the list: arrows move, Enter chooses, Esc or a click outside closes.
 * Left/right arrows on the closed drop-down step through the options.
 */
public class Dropdown<T> extends Widget {
    /** Draws an option's small picture. */
    @FunctionalInterface
    public interface Painter {
        void paint(Ui ui, int x, int y, int w, int h);
    }

    public record Option<T>(T value, Component label, Component description, Painter thumb) {
        public Option(T value, Component label) {
            this(value, label, null, null);
        }
    }

    private final Supplier<List<Option<T>>> options;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final Reveal reveal = new Reveal();
    /** The list on screen (also while it animates out), or null. */
    private Popup popup;
    private int thumbWidth = 22;

    public Dropdown(Supplier<List<Option<T>>> options, Supplier<T> getter, Consumer<T> setter) {
        this.options = options;
        this.getter = getter;
        this.setter = setter;
        this.h = Theme.CONTROL_HEIGHT;
    }

    /** Taller, with room for thumbnails of this width. */
    public Dropdown<T> thumbs(int width, int height) {
        thumbWidth = width;
        this.h = height;
        return this;
    }

    @Override
    protected int measure(int width) {
        return h;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    private Option<T> current() {
        T value = getter.get();
        for (Option<T> option : options.get()) {
            if (Objects.equals(option.value(), value)) {
                return option;
            }
        }
        List<Option<T>> all = options.get();
        return all.isEmpty() ? null : all.getFirst();
    }

    @Override
    protected void draw(Ui ui) {
        float o = popup != null ? reveal.eased() : 0f;
        int border = Theme.mix(Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, hover.value()), Theme.accent(),
                Math.max(focus.value(), o));
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, Theme.mix(Theme.FIELD, Theme.CARD, hover.value()), border);
        Option<T> option = current();
        int tx = x + 6;
        if (option != null && option.thumb() != null && h >= 20) {
            int th = h - 6;
            int tw = Math.min(thumbWidth, th * 3 / 2);
            ui.pushClip(x + 3, y + 3, tw, th);
            option.thumb().paint(ui, x + 3, y + 3, tw, th);
            ui.popClip();
            tx = x + 3 + tw + 5;
        }
        if (option != null) {
            ui.textFit(option.label(), tx, y + (h - 8) / 2, x + w - 16 - tx, Theme.TEXT);
        }
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, o * 1f);
        Draw.icon(ui, Icon.CHEVRON_DOWN, x + w - 13, y + (h - Icon.SIZE) / 2, 1,
                Theme.mix(Theme.TEXT_DIM, Theme.accent(), Math.max(hover.value(), o)));
        ui.g.pose().popMatrix();
    }

    public int optionCount() {
        return options.get().size();
    }

    /** Chooses option {@code index} (as a click on it in the list). */
    public void choose(int index) {
        List<Option<T>> all = options.get();
        if (index >= 0 && index < all.size()) {
            setter.accept(all.get(index).value());
        }
    }

    /** Open or opening (a list that is only animating out counts as closed). */
    public boolean isOpen() {
        return popup != null && reveal.wantsOpen();
    }

    /** The list's open/close state (tests). */
    public Reveal.State listState() {
        return reveal.state();
    }

    public void openList() {
        if (isOpen()) {
            return;
        }
        click();
        if (popup == null) {
            popup = new Popup();
            screen().openOverlay(popup);
        }
        // Opened again while still closing: the same list turns round from where it is.
        reveal.open();
        screen().setFocus(popup);
    }

    /** Closes the list with its (shorter) closing animation. */
    public void closeList() {
        if (!isOpen()) {
            return;
        }
        closeClick();
        reveal.close();
        if (screen() != null && screen().focused() == popup) {
            screen().setFocus(this);
        }
    }

    public void toggleList() {
        if (isOpen()) {
            closeList();
        } else {
            openList();
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        toggleList();
        return true;
    }

    @Override
    public boolean key(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_NUMPADENTER) {
            toggleList();
            return true;
        }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
            List<Option<T>> all = options.get();
            int index = all.indexOf(current());
            if (!all.isEmpty()) {
                int next = Math.floorMod(index + (key == InputConstants.KEY_RIGHT ? 1 : -1), all.size());
                setter.accept(all.get(next).value());
            }
            return true;
        }
        return false;
    }

    /** The open list. */
    private final class Popup extends UiScreen.Overlay {
        private static final int MAX_VISIBLE = 9;
        private final List<Option<T>> items = options.get();
        private int highlight = Math.max(0, items.indexOf(current()));
        private float scroll;
        private int itemH;

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        public void place(int screenWidth, int screenHeight) {
            boolean thumbs = items.stream().anyMatch(o -> o.thumb() != null);
            itemH = thumbs ? Math.max(20, Dropdown.this.h) : 16;
            int visible = Math.min(MAX_VISIBLE, items.size());
            int popupH = visible * itemH + 4;
            int popupW = Math.max(Dropdown.this.w, 150);
            int px = Math.min(Dropdown.this.x, screenWidth - popupW - 4);
            int below = Dropdown.this.y + Dropdown.this.h + 2;
            int py = below + popupH <= screenHeight - 4 ? below : Dropdown.this.y - popupH - 2;
            py = Math.clamp(py, 4, Math.max(4, screenHeight - popupH - 4));
            this.x = Math.max(4, px);
            this.y = py;
            this.w = popupW;
            this.h = popupH;
        }

        private int maxScroll() {
            return Math.max(0, items.size() * itemH + 4 - h);
        }

        @Override
        public boolean closing() {
            return !reveal.wantsOpen();
        }

        @Override
        public boolean ownsClick(double mx, double my) {
            // A click on the header toggles the list (Dropdown.mouseDown), it is not a click outside.
            return Dropdown.this.contains(mx, my);
        }

        @Override
        protected void draw(Ui ui) {
            if (reveal.tick(UiClock.dt()) == Reveal.State.CLOSED) {
                screen().closeOverlay(this);
                return;
            }
            float a = reveal.eased();
            ui.g.pose().pushMatrix();
            ui.g.pose().translate(0, (1f - a) * -4f);
            Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CONTROL, a);
            Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, Theme.fade(0xFF15161C, a), Theme.fade(Theme.BORDER_STRONG, a));
            if (a < 0.35f) {
                // Nearly gone (or just appearing): only the box, the items would flicker at this size of fade.
                ui.g.pose().popMatrix();
                return;
            }
            ui.pushClip(x + 1, y + 2, w - 2, h - 4);
            T chosen = getter.get();
            for (int i = 0; i < items.size(); i++) {
                Option<T> option = items.get(i);
                int iy = Math.round(y + 2 + i * itemH - scroll);
                if (iy + itemH < y || iy > y + h) {
                    continue;
                }
                boolean over = ui.mouseX >= x && ui.mouseX < x + w && ui.mouseY >= iy && ui.mouseY < iy + itemH
                        && ui.mouseY >= y && ui.mouseY < y + h;
                if (over) {
                    highlight = i;
                }
                if (i == highlight) {
                    Draw.roundRect(ui, x + 2, iy, w - 4, itemH, 3, Theme.accentAlpha(0x38));
                }
                int tx = x + 7;
                if (option.thumb() != null) {
                    int th = itemH - 4;
                    int tw = Math.min(thumbWidth, th * 3 / 2);
                    ui.pushClip(x + 4, iy + 2, tw, th);
                    option.thumb().paint(ui, x + 4, iy + 2, tw, th);
                    ui.popClip();
                    tx = x + 4 + tw + 6;
                }
                boolean isChosen = Objects.equals(option.value(), chosen);
                ui.textFit(option.label(), tx, iy + (itemH - 8) / 2, x + w - 18 - tx, isChosen ? Theme.accentHover() : Theme.TEXT);
                if (isChosen) {
                    Draw.icon(ui, Icon.CHECK, x + w - 14, iy + (itemH - Icon.SIZE) / 2, 1, Theme.accentHover());
                }
            }
            ui.popClip();
            if (maxScroll() > 0) {
                int barH = Math.max(10, h * h / (items.size() * itemH + 4));
                int barY = y + Math.round((h - barH) * (scroll / maxScroll()));
                Draw.roundRect(ui, x + w - 3, barY, 2, barH, 1, Theme.BORDER_STRONG);
            }
            ui.g.pose().popMatrix();
            Option<T> hovered = highlight >= 0 && highlight < items.size() ? items.get(highlight) : null;
            tooltip(hovered != null && hovered.description() != null ? hovered.label() : null,
                    hovered != null ? hovered.description() : null);
        }

        @Override
        public Widget hit(double mx, double my) {
            return !closing() && contains(mx, my) ? this : null;
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            int index = (int) Math.floor((my - y - 2 + scroll) / itemH);
            if (index >= 0 && index < items.size()) {
                choose(index);
            }
            return true;
        }

        @Override
        public boolean scroll(double mx, double my, double amount) {
            scroll = (float) Math.clamp(scroll - amount * itemH, 0, maxScroll());
            return true;
        }

        private void choose(int index) {
            if (closing()) {
                return;
            }
            click();
            setter.accept(items.get(index).value());
            // One sound for choosing: the list closes without its own click.
            reveal.close();
            screen().setFocus(Dropdown.this);
        }

        private void close() {
            closeList();
            screen().setFocus(Dropdown.this);
        }

        @Override
        public void escape() {
            close();
        }

        @Override
        protected void onClosed() {
            popupClosed();
        }

        @Override
        public void outsideClick() {
            closeList();
        }

        private void keepVisible() {
            float top = highlight * itemH;
            if (top < scroll) {
                scroll = top;
            } else if (top + itemH > scroll + h - 4) {
                scroll = top + itemH - (h - 4);
            }
            scroll = Math.clamp(scroll, 0, maxScroll());
        }

        @Override
        public boolean key(KeyEvent event) {
            int key = event.key();
            if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
                highlight = Math.floorMod(highlight + (key == InputConstants.KEY_DOWN ? 1 : -1), items.size());
                keepVisible();
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_NUMPADENTER) {
                choose(highlight);
                return true;
            }
            if (key == InputConstants.KEY_TAB) {
                close();
                return false;
            }
            return false;
        }
    }

    @Override
    public void focusChanged(boolean focused) {
        if (!focused && isOpen() && screen() != null && screen().focused() != popup) {
            closeList();
        }
    }

    /** Called by the screen when the popup is removed (by any path). */
    void popupClosed() {
        popup = null;
        reveal.reset();
    }
}
