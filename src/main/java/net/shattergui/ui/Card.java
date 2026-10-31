package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

import java.util.function.BooleanSupplier;

/**
 * A rounded card holding a column of widgets. Optionally clickable ({@link #onClick}): then it lifts and brightens on
 * hover, can be focused, and shows an accent border when {@link #selected}.
 */
public class Card extends Column {
    private Runnable onClick;
    private Runnable onDoubleClick;
    private BooleanSupplier selected = () -> false;
    private final Anim select = new Anim(0f, Theme.SPEED_HOVER);
    private final Anim appear = new Anim(0f, Theme.SPEED_APPEAR);
    private final Anim within = new Anim(0f, Theme.SPEED_HOVER);
    private int fill = Theme.CARD;

    public Card(int pad, int gap) {
        super(pad, gap);
    }

    public Card onClick(Runnable action) {
        onClick = action;
        return this;
    }

    /** The second click of a double click (the first one still runs {@link #onClick}). */
    public Card onDoubleClick(Runnable action) {
        onDoubleClick = action;
        return this;
    }

    @Override
    public boolean doubleClicked(double mx, double my) {
        if (onDoubleClick != null) {
            onDoubleClick.run();
            return true;
        }
        return false;
    }

    public Card selected(BooleanSupplier value) {
        selected = value;
        return this;
    }

    public Card fill(int color) {
        fill = color;
        return this;
    }

    @Override
    public boolean focusable() {
        return onClick != null;
    }

    @Override
    protected void draw(Ui ui) {
        float a = appear.target(1f).tick();
        float hv = within.target(onClick != null && ui.isWithin(this)).tick();
        float s = select.target(selected.getAsBoolean()).tick();
        float lift = hv * 1.5f + (1f - a) * 4f;
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, -lift + (1f - a) * 6f);
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CARD, 0.4f + hv * 0.6f);
        int bg = Theme.mix(Theme.mix(fill, Theme.CARD_HOVER, hv), Theme.accentSurface(), s);
        int border = Theme.mix(Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, hv), Theme.accent(), Math.max(s, focus.value() * 0.8f));
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, Theme.fade(bg, Math.max(a, 0.3f)), border);
        for (Widget child : children) {
            child.render(ui);
        }
        ui.g.pose().popMatrix();
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (onClick != null && button == 0) {
            click();
            onClick.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean key(KeyEvent event) {
        if (onClick != null && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE
                || event.key() == InputConstants.KEY_NUMPADENTER)) {
            click();
            onClick.run();
            return true;
        }
        return false;
    }
}
