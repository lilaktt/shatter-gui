package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A button: primary (accent), secondary (raised), ghost (flat until hovered) or danger. Text and/or an icon; with only
 * an icon it is square. Hover lightens, pressing sinks it by a pixel and shrinks it slightly; Enter or Space presses it
 * when focused.
 */
public class Button extends Widget {
    public enum Kind {
        PRIMARY, SECONDARY, GHOST, DANGER
    }

    private Component text;
    private Icon icon;
    private final Kind kind;
    private Runnable action;
    private final Anim press = new Anim(0f, Theme.SPEED_PRESS);
    private boolean down;
    private boolean selected;

    public Button(Component text, Icon icon, Kind kind, Runnable action) {
        this.text = text;
        this.icon = icon;
        this.kind = kind;
        this.action = action;
        this.h = Theme.CONTROL_HEIGHT;
    }

    public static Button icon(Icon icon, Component tooltip, Runnable action) {
        Button button = new Button(null, icon, Kind.GHOST, action);
        button.tooltip(tooltip, null);
        return button;
    }

    public Button text(Component value) {
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

    /** Marks a button that leaves this screen or opens something outside the game (a folder). */
    public Button leavesScreen(boolean value) {
        leavesScreen = value;
        return this;
    }

    public boolean leavesScreen() {
        return leavesScreen;
    }

    /** Drawn as pressed-in (a toggled tool button). */
    public Button selected(boolean value) {
        selected = value;
        return this;
    }

    public Component text() {
        return text;
    }

    /** Natural width: text and icon plus padding. */
    public int naturalWidth() {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int width = 12;
        if (icon != null) {
            width += Icon.SIZE;
        }
        if (text != null) {
            width += font.width(text) + (icon != null ? 4 : 0);
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
                fill = Theme.mix(Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hv), Theme.CARD, p);
                border = Theme.mix(Theme.BORDER_STRONG, Theme.accentAlpha(0xC0), Math.max(hv * 0.5f, 0));
                textColor = Theme.TEXT;
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
        int contentW = (icon != null ? Icon.SIZE : 0) + (text != null ? ui.font.width(text) + (icon != null ? 4 : 0) : 0);
        int cx = x + Math.max(4, (w - contentW) / 2);
        int cy = y + (h - 8) / 2;
        if (icon != null) {
            Draw.icon(ui, icon, cx, y + (h - Icon.SIZE) / 2, 1, textColor);
            cx += Icon.SIZE + 4;
        }
        if (text != null) {
            int room = x + w - 4 - cx;
            ui.textFit(text, cx, cy, room, textColor);
            // A shortened label: the tooltip says it in full.
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
    public boolean key(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE
                || event.key() == InputConstants.KEY_NUMPADENTER) {
            press();
            return true;
        }
        return false;
    }
}
