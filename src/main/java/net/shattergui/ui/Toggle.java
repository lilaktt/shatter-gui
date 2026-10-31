package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A pill-shaped on/off switch; the knob slides and the track fills with the accent. Enter/Space/arrows switch it. */
public class Toggle extends Widget {
    public static final int WIDTH = 28;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final Anim knob;

    public Toggle(BooleanSupplier getter, Consumer<Boolean> setter) {
        this.getter = getter;
        this.setter = setter;
        this.knob = new Anim(getter.getAsBoolean() ? 1f : 0f, Theme.SPEED_INDICATOR);
        this.w = WIDTH;
        this.h = 14;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected int measure(int width) {
        return 14;
    }

    @Override
    public void layout(int x, int y, int w) {
        super.layout(x + w - WIDTH, y, WIDTH);
    }

    public void flip() {
        click();
        setter.accept(!getter.getAsBoolean());
    }

    @Override
    protected void draw(Ui ui) {
        float k = knob.target(getter.getAsBoolean()).tick();
        int track = Theme.mix(Theme.mix(Theme.RAISED, Theme.RAISED_HOVER, hover.value()), Theme.accent(), k);
        Draw.roundBox(ui, x, y, w, h, h / 2, track, Theme.mix(Theme.BORDER_STRONG, Theme.accentHover(), k));
        if (focus.value() > 0.05f) {
            Draw.roundOutline(ui, x - 1, y - 1, w + 2, h + 2, h / 2 + 1, Theme.accentAlpha(Math.round(200 * focus.value())));
        }
        int r = h / 2 - 2;
        int cx = Math.round(x + 2 + r + k * (w - 4 - 2 * r));
        Draw.circle(ui, cx, y + h / 2, r, 0xFFF5F6FA);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button == 0) {
            flip();
            return true;
        }
        return false;
    }

    @Override
    public boolean key(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_NUMPADENTER
                || key == InputConstants.KEY_LEFT && getter.getAsBoolean() || key == InputConstants.KEY_RIGHT && !getter.getAsBoolean()) {
            flip();
            return true;
        }
        return false;
    }
}
