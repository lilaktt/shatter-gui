package net.shattergui.ui;

import net.shattergui.platform.CharInput;
import net.shattergui.platform.KeyInput;
import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;

import java.util.List;

public abstract class Widget {
    public int x;
    public int y;
    public int w;
    public int h;
    Widget parent;
    public boolean visible = true;
    public boolean enabled = true;
    private Text tipTitle;
    private Text tipText;
    protected final Anim hover = new Anim(0f, Theme.SPEED_HOVER);
    protected final Anim focus = new Anim(0f, Theme.SPEED_HOVER);
    boolean broken;
    private String failure;

    public Widget tooltip(Text title, Text text) {
        tipTitle = title;
        tipText = text;
        return this;
    }

    public Text tooltipTitle() {
        return tipTitle;
    }

    public Text tooltipText() {
        return tipText;
    }

    public boolean tooltipCoversChildren() {
        return true;
    }

    public boolean focusable() {
        return false;
    }

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

    static final int BROKEN_HEIGHT = 14;

    public boolean broken() {
        return broken;
    }

    void markBroken(RuntimeException e, String action) {
        if (broken) {
            return;
        }
        broken = true;
        failure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        UiFailures.report(UiFailures.describe(action, this), e);
    }

    protected int measure(int width) {
        return h;
    }

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
            ui.restore(state);
            markBroken(e, "draw");
            drawBroken(ui);
        }
    }

    private void drawBroken(Ui ui) {
        if (w < 4 || h < 4) {
            return;
        }
        try {
            Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, 0xE03A1D22, Theme.DANGER);
            if (h >= 11) {
                Draw.icon(ui, Icon.WARNING, x + 3, y + Math.min((h - 9) / 2, 3), 1, Theme.DANGER);
                if (w > 18) {
                    ui.textFit(Text.translatable("ui.shatter_gui.widget_failed"), x + 15,
                            y + Math.min((h - 8) / 2, 3), w - 18, 0xFFFCA5A5);
                }
            }
            if (ui.isHovered(this)) {
                tooltip(Text.translatable("ui.shatter_gui.widget_failed"),
                        Text.literal(failure));
            }
        } catch (RuntimeException ignored) {
        }
    }

    protected abstract void draw(Ui ui);

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

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

    public boolean mouseDown(double mx, double my, int button) {
        return false;
    }

    public void mouseUp(double mx, double my, int button) {
    }

    public boolean doubleClicked(double mx, double my) {
        return false;
    }

    public void mouseDrag(double mx, double my) {
    }

    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    public boolean key(KeyInput event) {
        return false;
    }

    public boolean typed(CharInput event) {
        return false;
    }

    public void focusChanged(boolean focused) {
    }

    protected static void click() {
        Platform.get().sounds().buttonClick();
    }

    protected static void closeClick() {
        Platform.get().sounds().closeClick();
    }

    protected static UiScreen screen() {
        return UiScreen.active();
    }
}
