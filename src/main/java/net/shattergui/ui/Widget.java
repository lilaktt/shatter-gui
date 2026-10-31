package net.shattergui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Base of every editor widget. Layout is top-down each frame: the parent calls {@link #layout(int, int, int)} with a
 * position and width and the widget sets its height ({@link #measure}). Input arrives from {@link UiScreen}: the
 * topmost widget under the mouse gets the click (bubbling to its parents until one takes it), the focused widget gets
 * the keys. Hover and focus are animated ({@link #hover}, {@link #focus}, 0..1).
 */
public abstract class Widget {
    public int x;
    public int y;
    public int w;
    public int h;
    Widget parent;
    public boolean visible = true;
    public boolean enabled = true;
    private Component tipTitle;
    private Component tipText;
    protected final Anim hover = new Anim(0f, Theme.SPEED_HOVER);
    protected final Anim focus = new Anim(0f, Theme.SPEED_HOVER);
    /** Threw while drawing, laying out or handling input: shown as an error box, gets no more input. */
    boolean broken;
    private String failure;

    /** A tooltip: a short title and a one or two sentence description (either may be null). */
    public Widget tooltip(Component title, Component text) {
        tipTitle = title;
        tipText = text;
        return this;
    }

    public Component tooltipTitle() {
        return tipTitle;
    }

    public Component tooltipText() {
        return tipText;
    }

    /** Whether this tooltip also explains the widgets inside it that have none (a field's row: yes; a card: no). */
    public boolean tooltipCoversChildren() {
        return true;
    }

    public boolean focusable() {
        return false;
    }

    /** Places the widget; its height comes from {@link #measure}. */
    public void layout(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        if (!broken) {
            try {
                this.h = measure(w);
                return;
            } catch (RuntimeException e) {
                markBroken(e, "layout");
            }
        }
        this.h = Math.max(this.h, BROKEN_HEIGHT);
    }

    /** Places the widget with a fixed size. */
    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        if (broken) {
            return;
        }
        try {
            arrange();
        } catch (RuntimeException e) {
            markBroken(e, "layout");
        }
    }

    /** Height a broken widget keeps, so its error box can be seen. */
    static final int BROKEN_HEIGHT = 14;

    /** True once it failed (it is then drawn as an error box). */
    public boolean broken() {
        return broken;
    }

    /** Marks it broken; the failure is written to the log once per kind ({@link UiFailures}). */
    void markBroken(RuntimeException e, String action) {
        if (broken) {
            return;
        }
        broken = true;
        failure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        UiFailures.report(UiFailures.describe(action, this), e);
    }

    /** Height for a width; by default the current height. */
    protected int measure(int width) {
        return h;
    }

    /** Re-positions children after {@link #setBounds}; nothing for leaf widgets. */
    protected void arrange() {
    }

    public final void render(Ui ui) {
        if (!visible) {
            return;
        }
        if (broken) {
            drawBroken(ui);
            return;
        }
        Ui.State state = ui.save();
        try {
            hover.target(enabled && ui.isHovered(this)).tick();
            focus.target(ui.isFocused(this)).tick();
            draw(ui);
        } catch (RuntimeException e) {
            // Undo what the failed drawing left open (clips, transforms), then show the error box instead.
            ui.restore(state);
            markBroken(e, "draw");
            drawBroken(ui);
        }
    }

    /** The error box in the widget's place: a dark red frame, a warning sign and what failed. */
    private void drawBroken(Ui ui) {
        if (w < 4 || h < 4) {
            return;
        }
        try {
            Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, 0xE03A1D22, Theme.DANGER);
            if (h >= 11) {
                Draw.icon(ui, Icon.WARNING, x + 3, y + Math.min((h - 9) / 2, 3), 1, Theme.DANGER);
                if (w > 18) {
                    ui.textFit(net.minecraft.network.chat.Component.translatable("ui.shatter_gui.widget_failed"), x + 15,
                            y + Math.min((h - 8) / 2, 3), w - 18, 0xFFFCA5A5);
                }
            }
            if (ui.isHovered(this)) {
                tooltip(net.minecraft.network.chat.Component.translatable("ui.shatter_gui.widget_failed"),
                        net.minecraft.network.chat.Component.literal(failure));
            }
        } catch (RuntimeException ignored) {
            // Even the error box could not be drawn: leave the space empty.
        }
    }

    protected abstract void draw(Ui ui);

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** The deepest visible widget at the point, or null. */
    public Widget hit(double mx, double my) {
        return visible && contains(mx, my) ? this : null;
    }

    public void collectFocusable(List<Widget> out) {
        if (visible && enabled && focusable()) {
            out.add(this);
        }
    }

    public Widget parent() {
        return parent;
    }

    // --- input (all return true when handled) ---

    public boolean mouseDown(double mx, double my, int button) {
        return false;
    }

    public void mouseUp(double mx, double my, int button) {
    }

    /** The second click of a double click (instead of {@link #mouseDown}); true if handled. */
    public boolean doubleClicked(double mx, double my) {
        return false;
    }

    public void mouseDrag(double mx, double my) {
    }

    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    public boolean key(KeyEvent event) {
        return false;
    }

    public boolean typed(CharacterEvent event) {
        return false;
    }

    public void focusChanged(boolean focused) {
    }

    protected static void click() {
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
    }

    /** The click of a pop-up closing: quieter and a little higher than {@link #click()}. */
    protected static void closeClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.35f));
    }

    protected static UiScreen screen() {
        return UiScreen.active();
    }
}
