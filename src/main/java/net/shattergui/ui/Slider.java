package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * A slider with its value in a box on the right. Drag or click the track; arrows (Shift: ×10) step it when focused.
 * Clicking the value box (or Enter) turns it into a text field for an exact value, typed in display units (e.g.
 * seconds) and converted back by {@link #parser}; Enter applies, Esc cancels.
 */
public class Slider extends Container {
    public static final int VALUE_WIDTH = 60;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> formatter;
    private final Function<String, Double> parser;
    private final TextField field;
    private final Anim knob = new Anim(0f, 18f);
    private boolean dragging;
    private boolean editing;

    /**
     * @param formatter raw value → shown text (with units)
     * @param parser    typed text → raw value, or null if it is not a number
     */
    public Slider(double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter,
                  DoubleFunction<String> formatter, Function<String, Double> parser) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.formatter = formatter;
        this.parser = parser;
        this.h = Theme.CONTROL_HEIGHT;
        this.field = new TextField(16) {
            @Override
            public void focusChanged(boolean focused) {
                super.focusChanged(focused);
                if (!focused && editing) {
                    finishEdit(true);
                }
            }
        };
        field.filter(c -> c >= '0' && c <= '9' || c == '.' || c == ',' || c == '-');
        field.onEnter(() -> {
            finishEdit(true);
            screen().setFocus(this);
        });
        field.onEscape(() -> {
            finishEdit(false);
            screen().setFocus(this);
        });
        field.visible = false;
        add(field);
        knob.snap((float) fraction());
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected int measure(int width) {
        return Theme.CONTROL_HEIGHT;
    }

    private int trackX() {
        return x + 4;
    }

    private int trackW() {
        return w - VALUE_WIDTH - 12;
    }

    private double fraction() {
        return max > min ? (Math.clamp(getter.getAsDouble(), min, max) - min) / (max - min) : 0;
    }

    @Override
    protected void draw(Ui ui) {
        field.setBounds(x + w - VALUE_WIDTH, y, VALUE_WIDTH, h);
        float k = knob.target((float) fraction()).tick();
        if (dragging) {
            knob.snap((float) fraction());
            k = knob.value();
        }
        int tx = trackX();
        int tw = trackW();
        int cy = y + h / 2;
        boolean active = ui.isHovered(this) || dragging;
        Draw.roundRect(ui, tx, cy - 2, tw, 4, 2, Theme.RAISED_HOVER);
        Draw.roundRect(ui, tx, cy - 2, Math.round(tw * k), 4, 2, enabled ? Theme.accent() : Theme.TEXT_FAINT);
        int kx = Math.round(tx + tw * k);
        int r = active || focus.value() > 0.5f ? 5 : 4;
        Draw.circle(ui, kx, cy, r + 1, Theme.mix(Theme.BORDER_STRONG, Theme.accent(), Math.max(focus.value(), hover.value())));
        Draw.circle(ui, kx, cy, r, 0xFFF5F6FA);
        if (!editing) {
            int bx = x + w - VALUE_WIDTH;
            boolean overBox = ui.mouseX >= bx && ui.isHovered(this);
            Draw.roundBox(ui, bx, y, VALUE_WIDTH, h, Theme.RADIUS_CONTROL, Theme.FIELD,
                    overBox ? Theme.accentAlpha(0xC0) : Theme.BORDER);
            String text = formatter.apply(getter.getAsDouble());
            int textW = Math.min(ui.font.width(text), VALUE_WIDTH - 6);
            ui.textFit(net.minecraft.network.chat.Component.literal(text), bx + Math.max(3, (VALUE_WIDTH - textW) / 2),
                    y + (h - 8) / 2, VALUE_WIDTH - 6, Theme.TEXT);
        }
        super.draw(ui);
    }

    @Override
    public Widget hit(double mx, double my) {
        if (editing) {
            Widget hit = field.hit(mx, my);
            if (hit != null) {
                return hit;
            }
        }
        return visible && contains(mx, my) ? this : null;
    }

    @Override
    public void collectFocusable(java.util.List<Widget> out) {
        if (visible && enabled) {
            out.add(editing ? field : this);
        }
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** Sets a value as the track would (clamped). */
    public void setValue(double value) {
        if (Double.isFinite(value)) {
            setter.accept(Math.clamp(value, min, max));
        }
    }

    /** The exact-value box (tests type into it). */
    public TextField field() {
        return field;
    }

    public void startEdit() {
        editing = true;
        field.visible = true;
        field.value(formatter.apply(getter.getAsDouble()).replaceAll("[^0-9.,-]", ""));
        field.selectAll();
        screen().setFocus(field);
        field.selectAll();
    }

    private void finishEdit(boolean apply) {
        if (!editing) {
            return;
        }
        editing = false;
        field.visible = false;
        if (apply) {
            Double raw = parser.apply(field.value());
            if (raw != null && Double.isFinite(raw)) {
                setter.accept(Math.clamp(raw, min, max));
            }
        }
    }

    private void setFrom(double mx) {
        double f = Math.clamp((mx - trackX()) / Math.max(1, trackW()), 0, 1);
        double value = min + (max - min) * f;
        if (step > 0) {
            value = min + Math.round((value - min) / step) * step;
        }
        setter.accept(Math.clamp(value, min, max));
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (mx >= x + w - VALUE_WIDTH) {
            startEdit();
            return true;
        }
        dragging = true;
        setFrom(mx);
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my) {
        if (dragging) {
            setFrom(mx);
        }
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        dragging = false;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    @Override
    public boolean key(KeyEvent event) {
        int key = event.key();
        double s = step > 0 ? step : (max - min) / 100;
        if (event.hasShiftDown()) {
            s *= 10;
        }
        if (key == InputConstants.KEY_LEFT) {
            setter.accept(Math.clamp(getter.getAsDouble() - s, min, max));
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            setter.accept(Math.clamp(getter.getAsDouble() + s, min, max));
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            startEdit();
            return true;
        }
        return false;
    }
}
