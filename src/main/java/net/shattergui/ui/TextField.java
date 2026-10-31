package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * A one-line text field: caret, selection (Shift+arrows, Ctrl+A, double use of Home/End), clipboard (Ctrl+C/X/V),
 * a hint when empty, a character filter and a length limit. Enter calls {@link #onEnter}, Esc {@link #onEscape} if set.
 */
public class TextField extends Widget {
    private String value = "";
    private int cursor;
    private int anchor;
    private int scroll;
    private final int maxLength;
    private Component hint;
    private Consumer<String> onChange = s -> { };
    private Runnable onEnter;
    private Runnable onEscape;
    private IntPredicate filter = c -> c >= 32 && c != 127;
    private Icon icon;

    public TextField(int maxLength) {
        this.maxLength = maxLength;
        this.h = Theme.CONTROL_HEIGHT;
    }

    public TextField hint(Component hint) {
        this.hint = hint;
        return this;
    }

    public TextField icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public TextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TextField onEnter(Runnable onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    public TextField onEscape(Runnable onEscape) {
        this.onEscape = onEscape;
        return this;
    }

    public TextField filter(IntPredicate filter) {
        this.filter = filter;
        return this;
    }

    public String value() {
        return value;
    }

    /** Sets the text without calling the change listener. */
    public TextField value(String text) {
        value = text.length() > maxLength ? text.substring(0, maxLength) : text;
        cursor = value.length();
        anchor = cursor;
        return this;
    }

    /** Replaces the text as typing would (the filter and length limit apply, the change listener runs). */
    public void typeText(String text) {
        selectAll();
        insert(text);
    }

    public void selectAll() {
        anchor = 0;
        cursor = value.length();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    private int textX() {
        return x + 5 + (icon != null ? 12 : 0);
    }

    private int textWidth() {
        return w - 10 - (icon != null ? 12 : 0);
    }

    @Override
    protected void draw(Ui ui) {
        float f = focus.value();
        int border = Theme.mix(Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, hover.value()), Theme.accent(), f);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, Theme.FIELD, border);
        if (icon != null) {
            Draw.icon(ui, icon, x + 5, y + (h - Icon.SIZE) / 2, 1, Theme.TEXT_FAINT);
        }
        int tx = textX();
        int tw = textWidth();
        int ty = y + (h - 8) / 2;
        if (value.isEmpty() && hint != null && f < 0.5f) {
            ui.textFit(hint, tx, ty, tw, Theme.TEXT_FAINT);
        }
        // Keep the caret inside the visible part.
        int caretX = ui.font.width(value.substring(0, cursor));
        if (caretX - scroll > tw - 2) {
            scroll = caretX - tw + 2;
        } else if (caretX - scroll < 0) {
            scroll = caretX;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ui.font.width(value) - tw + 2)));
        ui.pushClip(tx - 1, y + 1, tw + 2, h - 2);
        if (cursor != anchor && ui.isFocused(this)) {
            int a = ui.font.width(value.substring(0, Math.min(cursor, anchor))) - scroll;
            int b = ui.font.width(value.substring(0, Math.max(cursor, anchor))) - scroll;
            Draw.rect(ui, tx + a, ty - 1, b - a, 10, Theme.accentAlpha(0x70));
        }
        ui.text(value, tx - scroll, ty, enabled ? Theme.TEXT : Theme.TEXT_FAINT);
        if (ui.isFocused(this) && (UiClock.time() % 1.0) < 0.55) {
            Draw.rect(ui, tx + caretX - scroll, ty - 1, 1, 10, Theme.accent());
        }
        ui.popClip();
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        int rel = (int) mx - textX() + scroll;
        var font = Minecraft.getInstance().font;
        int pos = font.plainSubstrByWidth(value, Math.max(0, rel)).length();
        if (pos < value.length() && rel > font.width(value.substring(0, pos)) + font.width(value.substring(pos, pos + 1)) / 2) {
            pos++;
        }
        cursor = Math.min(pos, value.length());
        anchor = cursor;
        return true;
    }

    @Override
    public void focusChanged(boolean focused) {
        if (focused) {
            cursor = value.length();
            anchor = 0;
        } else {
            anchor = cursor;
        }
    }

    @Override
    public boolean key(KeyEvent event) {
        int key = event.key();
        boolean shift = event.hasShiftDown();
        if (event.isSelectAll()) {
            selectAll();
            return true;
        }
        if (event.isCopy()) {
            if (cursor != anchor) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected());
            }
            return true;
        }
        if (event.isCut()) {
            if (cursor != anchor) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected());
                insert("");
            }
            return true;
        }
        if (event.isPaste()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        switch (key) {
            case InputConstants.KEY_LEFT -> move(cursor - 1, shift);
            case InputConstants.KEY_RIGHT -> move(cursor + 1, shift);
            case InputConstants.KEY_HOME -> move(0, shift);
            case InputConstants.KEY_END -> move(value.length(), shift);
            case InputConstants.KEY_BACKSPACE -> {
                if (cursor == anchor && cursor > 0) {
                    anchor = cursor - 1;
                }
                insert("");
            }
            case InputConstants.KEY_DELETE -> {
                if (cursor == anchor && cursor < value.length()) {
                    anchor = cursor + 1;
                }
                insert("");
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (onEnter == null) {
                    return false;
                }
                onEnter.run();
            }
            case InputConstants.KEY_ESCAPE -> {
                if (onEscape == null) {
                    return false;
                }
                onEscape.run();
            }
            default -> {
                // Letters and other keys arrive as characters; Tab and arrows up/down move focus.
                return key != InputConstants.KEY_TAB && key != InputConstants.KEY_UP && key != InputConstants.KEY_DOWN
                        && key >= 32 && key < 256;
            }
        }
        return true;
    }

    @Override
    public boolean typed(CharacterEvent event) {
        if (!filter.test(event.codepoint())) {
            return false;
        }
        insert(event.codepointAsString());
        return true;
    }

    private String selected() {
        return value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor));
    }

    private void move(int to, boolean select) {
        cursor = Math.clamp(to, 0, value.length());
        if (!select) {
            anchor = cursor;
        }
    }

    private void insert(String text) {
        StringBuilder clean = new StringBuilder();
        text.codePoints().filter(filter).forEach(clean::appendCodePoint);
        int a = Math.min(cursor, anchor);
        int b = Math.max(cursor, anchor);
        String next = value.substring(0, a) + clean + value.substring(b);
        if (next.length() > maxLength) {
            next = next.substring(0, maxLength);
        }
        value = next;
        cursor = Math.min(a + clean.length(), value.length());
        anchor = cursor;
        onChange.accept(value);
    }
}
