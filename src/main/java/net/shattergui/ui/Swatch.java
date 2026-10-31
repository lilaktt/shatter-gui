package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** A round colour choice; the chosen one has a ring and a check mark. */
public class Swatch extends Widget {
    private final int color;
    private final BooleanSupplier chosen;
    private final Runnable action;
    private final Anim ring = new Anim(0f, Theme.SPEED_HOVER);

    public Swatch(int color, Component name, BooleanSupplier chosen, Runnable action) {
        this.color = 0xFF000000 | color;
        this.chosen = chosen;
        this.action = action;
        this.w = 18;
        this.h = 18;
        tooltip(name, null);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected int measure(int width) {
        return 18;
    }

    @Override
    public void layout(int x, int y, int w) {
        super.layout(x, y, 18);
    }

    @Override
    protected void draw(Ui ui) {
        float r = ring.target(chosen.getAsBoolean()).tick();
        int cx = x + 9;
        int cy = y + 9;
        if (r > 0.02f || hover.value() > 0.02f || focus.value() > 0.02f) {
            Draw.circle(ui, cx, cy, 9, Theme.fade(0xFFFFFFFF, Math.max(r, Math.max(hover.value() * 0.4f, focus.value()))));
            Draw.circle(ui, cx, cy, 8, Theme.PANEL | 0xFF000000);
        }
        Draw.circle(ui, cx, cy, 6 + Math.round(hover.value()), color);
        if (r > 0.5f) {
            Draw.icon(ui, Icon.CHECK, cx - 4, cy - 4, 1, Theme.fade(0xFFFFFFFF, r));
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button == 0) {
            click();
            action.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean key(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) {
            click();
            action.run();
            return true;
        }
        return false;
    }
}
