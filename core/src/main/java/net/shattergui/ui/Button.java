package net.shattergui.ui;

import net.shattergui.platform.KeyInput;
import net.shattergui.platform.Keys;
import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;

public class Button extends Widget {
    public enum Kind {
        PRIMARY, SECONDARY, GHOST, DANGER
    }

    private Text text;
    private Icon icon;
    private final Kind kind;
    private Runnable action;
    private final Anim press = new Anim(0f, Theme.SPEED_PRESS);
    private boolean down;
    private boolean selected;
    private int brand;
    private IconPainter painter;

    public interface IconPainter {
        void paint(Ui ui, int x, int y, int color, float fill);
    }

    private static final float BRAND_FILL = 1f;

    public Button(Text text, Icon icon, Kind kind, Runnable action) {
        this.text = text;
        this.icon = icon;
        this.kind = kind;
        this.action = action;
        this.h = Theme.CONTROL_HEIGHT;
    }

    public static Button icon(Icon icon, Text tooltip, Runnable action) {
        Button button = new Button(null, icon, Kind.GHOST, action);
        button.tooltip(tooltip, null);
        return button;
    }

    public Button text(Text value) {
        text = value;
        return this;
    }

    public Button action(Runnable value) {
        action = value;
        return this;
    }

    private boolean leavesScreen;

    public Icon icon() {
        return icon;
    }

    public Kind kind() {
        return kind;
    }

    public Button leavesScreen(boolean value) {
        leavesScreen = value;
        return this;
    }

    public boolean leavesScreen() {
        return leavesScreen;
    }

    public Button brand(int argb) {
        brand = argb;
        return this;
    }

    public int brand() {
        return brand;
    }

    public Button iconPainter(IconPainter value) {
        painter = value;
        return this;
    }

    public float brandFill() {
        return brand == 0 ? 0f : Math.max(hover.value(), focus.value());
    }

    public static int[] brandColors(int brand, float hover, float focus, float press) {
        float t = Math.max(hover, focus);
        int base = Theme.mix(Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hover), Theme.CARD, press);
        int fill = Theme.mix(base, brand, t * BRAND_FILL);
        int border = Theme.mix(Theme.BORDER_STRONG, brand, t);
        return new int[]{fill, Contrast.readableOn(fill, Theme.TEXT), border};
    }

    public Button selected(boolean value) {
        selected = value;
        return this;
    }

    public Text text() {
        return text;
    }

    public int naturalWidth() {
        var font = Platform.get().texts().font();
        int width = 12;
        boolean hasIcon = icon != null || painter != null;
        if (hasIcon) {
            width += Icon.SIZE;
        }
        if (text != null) {
            width += font.width(text) + (hasIcon ? 4 : 0);
        }
        return text == null ? Theme.CONTROL_HEIGHT : width;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    public void press() {
        if (enabled && action != null) {
            click();
            action.run();
        }
    }

    @Override
    protected void draw(Ui ui) {
        float hv = hover.value();
        float p = press.target(down && ui.isHovered(this)).tick();
        int fill;
        int border;
        int textColor;
        switch (kind) {
            case PRIMARY -> {
                fill = Theme.mix(Theme.mix(Theme.accent(), Theme.accentHover(), hv), Theme.accentPressed(), p);
                border = Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.15f);
                textColor = Theme.TEXT_ON_ACCENT;
            }
            case DANGER -> {
                fill = Theme.mix(Theme.mix(0xFF3A1D22, 0xFF5A222A, hv), 0xFF2A1418, p);
                border = Theme.mix(0xFF5A2A30, Theme.DANGER, hv);
                textColor = Theme.mix(0xFFFCA5A5, 0xFFFFFFFF, hv);
            }
            case GHOST -> {
                fill = Theme.mix(selected ? Theme.accentAlpha(0x40) : 0x00000000, Theme.RAISED_HOVER, Math.max(hv * 0.85f, 0));
                border = selected ? Theme.accentAlpha(0xA0) : 0x00000000;
                textColor = Theme.mix(selected ? Theme.TEXT : Theme.TEXT_DIM, Theme.TEXT, hv);
            }
            default -> {
                if (brand != 0) {
                    int[] colors = brandColors(brand, Math.max(hv, 0f), focus.value(), p);
                    fill = colors[0];
                    textColor = colors[1];
                    border = colors[2];
                } else {
                    fill = Theme.mix(Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hv), Theme.CARD, p);
                    border = Theme.mix(Theme.BORDER_STRONG, Theme.accentAlpha(0xC0), Math.max(hv * 0.5f, 0));
                    textColor = Theme.TEXT;
                }
            }
        }
        if (!enabled) {
            fill = Theme.fade(fill, 0.45f);
            border = Theme.fade(border, 0.45f);
            textColor = Theme.TEXT_FAINT;
        }
        ui.g.pose().pushMatrix();
        float scale = 1f - 0.04f * p;
        ui.g.pose().translate(x + w / 2f, y + h / 2f + p);
        ui.g.pose().scale(scale, scale);
        ui.g.pose().translate(-(x + w / 2f), -(y + h / 2f));
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, fill, border);
        if (focus.value() > 0.05f) {
            Draw.roundOutline(ui, x - 1, y - 1, w + 2, h + 2, Theme.RADIUS_CONTROL + 1, Theme.accentAlpha(Math.round(200 * focus.value())));
        }
        boolean hasIcon = icon != null || painter != null;
        int contentW = (hasIcon ? Icon.SIZE : 0) + (text != null ? ui.font.width(text) + (hasIcon ? 4 : 0) : 0);
        int cx = x + Math.max(4, (w - contentW) / 2);
        int cy = y + (h - 8) / 2;
        if (painter != null) {
            painter.paint(ui, cx, cy, textColor, brandFill());
            cx += Icon.SIZE + 4;
        } else if (icon != null) {
            Draw.icon(ui, icon, cx, y + (h - Icon.SIZE) / 2, 1, textColor);
            cx += Icon.SIZE + 4;
        }
        if (text != null) {
            int room = x + w - 4 - cx;
            ui.textFit(text, cx, cy, room, textColor);
            if (ui.font.width(text) > room && tooltipTitle() == null) {
                tooltip(text, null);
            }
        }
        ui.g.pose().popMatrix();
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        down = true;
        return true;
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        boolean inside = contains(mx, my);
        down = false;
        if (inside) {
            press();
        }
    }

    @Override
    public boolean key(KeyInput event) {
        if (event.key() == Keys.KEY_RETURN || event.key() == Keys.KEY_SPACE
                || event.key() == Keys.KEY_NUMPADENTER) {
            press();
            return true;
        }
        return false;
    }
}
