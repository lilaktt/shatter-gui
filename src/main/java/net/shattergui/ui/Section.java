package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A group of settings as a card: a slightly lighter surface with a 1-pixel border and rounded corners, a header with
 * a small icon, the title and a chevron, a thin line under it, and the rows inside separated by thin lines. A click on
 * the header (or Enter/Space when focused) folds the card; the fold state is remembered for the session by its key.
 * Folding and unfolding glide.
 */
public class Section extends Container {
    public static final int HEADER = 18;
    public static final int PAD = 6;
    private static final Map<String, Boolean> FOLDED = new HashMap<>();

    private final String key;
    private final Icon icon;
    private final Supplier<Component> title;
    private final Column body;
    private final boolean foldable;
    private final Anim open;
    private Supplier<Component> badge = () -> null;
    private int badgeColor = Theme.TEXT_FAINT;
    /** The "?" in the header (a longer explanation; a click opens the help), or null. */
    private HelpBadge help;

    /** A "?" in the header: hovering shows {@code text} under {@code title}, a click (or Enter) runs {@code open}. */
    public Section help(Component title, Component text, Runnable open) {
        help = new HelpBadge(title, text, open);
        super.add(help);
        return this;
    }

    /** The round "?" of a card's header. */
    public static final class HelpBadge extends Widget {
        private final Runnable open;

        HelpBadge(Component title, Component text, Runnable open) {
            this.open = open;
            tooltip(title, text);
        }

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        protected void draw(Ui ui) {
            int color = Theme.mix(Theme.TEXT_FAINT, Theme.accentHover(), Math.max(hover.value(), focus.value()));
            Draw.circle(ui, x + w / 2, y + h / 2, w / 2, Theme.mix(Theme.CARD, Theme.RAISED_HOVER, hover.value()));
            Draw.icon(ui, Icon.HELP, x + (w - Icon.SIZE) / 2, y + (h - Icon.SIZE) / 2, 1, color);
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            if (button != 0 || open == null) {
                return false;
            }
            click();
            open.run();
            return true;
        }

        @Override
        public boolean key(KeyEvent event) {
            if (open != null && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE)) {
                click();
                open.run();
                return true;
            }
            return false;
        }
    }

    /**
     * @param key            remembers the fold state for the session (null: not foldable)
     * @param foldedByDefault the state before the player changes it
     */
    public Section(String key, Icon icon, Supplier<Component> title, boolean foldedByDefault) {
        this(key, icon, title, foldedByDefault, 4);
    }

    public Section(String key, Icon icon, Supplier<Component> title, boolean foldedByDefault, int gap) {
        this.key = key;
        this.icon = icon;
        this.title = title;
        this.foldable = key != null;
        this.body = new Column(0, gap);
        super.add(body);
        boolean folded = foldable && FOLDED.getOrDefault(key, foldedByDefault);
        this.open = new Anim(folded ? 0f : 1f, Theme.SPEED_APPEAR + 3f);
    }

    public Section(String key, Icon icon, Component title, boolean foldedByDefault) {
        this(key, icon, () -> title, foldedByDefault);
    }

    public Section(String key, Icon icon, Component title, boolean foldedByDefault, int gap) {
        this(key, icon, () -> title, foldedByDefault, gap);
    }

    /** Text on the right of the header (a count, problems), or null. */
    public Section badge(Supplier<Component> value, int color) {
        badge = value;
        badgeColor = color;
        return this;
    }

    /** Adds a row to the card's body. */
    @Override
    public <T extends Widget> T add(T child) {
        return body.add(child);
    }

    @Override
    public void clear() {
        body.clear();
    }

    public Column body() {
        return body;
    }

    public boolean folded() {
        return open.targetValue() < 0.5f;
    }

    public void setFolded(boolean value) {
        if (!foldable) {
            return;
        }
        open.target(!value);
        FOLDED.put(key, value);
    }

    /** Forgets every remembered fold state (tests). */
    public static void forgetFolds() {
        FOLDED.clear();
    }

    @Override
    public boolean focusable() {
        return foldable;
    }

    /** The header's tooltip (fold, unfold) is about the card, not about its rows. */
    @Override
    public boolean tooltipCoversChildren() {
        return false;
    }

    @Override
    protected int measure(int width) {
        body.layout(x + PAD, y + HEADER + PAD, Math.max(10, width - PAD * 2));
        if (help != null) {
            int right = x + width - PAD - (foldable ? Icon.SIZE + 2 : 0);
            help.setBounds(right - 13, y + (HEADER - 11) / 2, 11, 11);
        }
        float o = open.value();
        int full = body.h + PAD * 2;
        return HEADER + Math.round(full * o);
    }

    @Override
    protected void draw(Ui ui) {
        float o = open.target(open.targetValue()).tick();
        boolean over = foldable && ui.isHovered(this) && ui.mouseY < y + HEADER;
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, Theme.CARD, Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, over ? 1f : 0f));
        if (focus.value() > 0.05f) {
            Draw.roundOutline(ui, x, y, w, h, Theme.RADIUS_CARD, Theme.accentAlpha(Math.round(180 * focus.value())));
        }
        // Header: icon, title, badge, chevron.
        int tx = x + PAD;
        if (icon != null) {
            Draw.icon(ui, icon, tx, y + (HEADER - Icon.SIZE) / 2, 1, Theme.accentHover());
            tx += Icon.SIZE + 5;
        }
        int right = x + w - PAD;
        if (foldable) {
            right -= Icon.SIZE + 2;
            ui.g.pose().pushMatrix();
            float cx = right + 2 + Icon.SIZE / 2f;
            float cy = y + HEADER / 2f;
            ui.g.pose().translate(cx, cy);
            ui.g.pose().rotate((float) (-Math.PI / 2 * (1 - o)));
            ui.g.pose().translate(-Icon.SIZE / 2f, -Icon.SIZE / 2f);
            Draw.icon(ui, Icon.CHEVRON_DOWN, 0, 0, 1, over ? Theme.TEXT : Theme.TEXT_FAINT);
            ui.g.pose().popMatrix();
        }
        if (help != null) {
            help.render(ui);
            right = help.x - 3;
        }
        Component b = badge.get();
        Component t = title.get();
        if (b != null && ui.font.width(t) + 6 + ui.font.width(b) > right - tx) {
            // Too narrow for both: the title wins (the badge is a detail).
            b = null;
        }
        if (b != null) {
            int bw = Math.min(ui.font.width(b), (right - tx) / 2);
            ui.textFit(b, right - bw - 2, y + (HEADER - 8) / 2, bw, badgeColor);
            right -= bw + 6;
        }
        ui.textFit(t, tx, y + (HEADER - 8) / 2, right - tx, Theme.TEXT);
        if (foldable) {
            tooltip(t, Component.translatable(folded() ? "ui.shatter_gui.section.unfold" : "ui.shatter_gui.section.fold"));
        } else if (ui.font.width(t) > right - tx && tooltipTitle() == null) {
            tooltip(t, null);
        }
        if (o < 0.01f) {
            return;
        }
        // The body, with a line under the header and thin lines between rows; clipped while folding.
        Draw.rect(ui, x + 1, y + HEADER - 1, w - 2, 1, Theme.fade(Theme.BORDER, o));
        ui.pushClip(x + 1, y + HEADER, w - 2, h - HEADER - 1);
        Widget previous = null;
        for (Widget row : body.children()) {
            if (!row.visible) {
                continue;
            }
            if (previous != null) {
                int ly = (previous.y + previous.h + row.y) / 2;
                Draw.rect(ui, x + PAD, ly, w - PAD * 2, 1, 0x14FFFFFF);
            }
            previous = row;
        }
        body.render(ui);
        ui.popClip();
    }

    @Override
    public Widget hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        if (help != null && help.contains(mx, my)) {
            return help;
        }
        if (my >= y + HEADER && open.value() > 0.5f) {
            Widget hit = body.hit(mx, my);
            if (hit != null && hit != body) {
                return hit;
            }
        }
        return this;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button == 0 && foldable && my < y + HEADER) {
            click();
            setFolded(!folded());
            return true;
        }
        return false;
    }

    @Override
    public boolean key(KeyEvent event) {
        int k = event.key();
        if (foldable && (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_SPACE || k == InputConstants.KEY_NUMPADENTER)) {
            click();
            setFolded(!folded());
            return true;
        }
        return false;
    }

    @Override
    public void collectFocusable(java.util.List<Widget> out) {
        if (!visible) {
            return;
        }
        if (focusable()) {
            out.add(this);
        }
        if (help != null) {
            out.add(help);
        }
        if (open.targetValue() > 0.5f) {
            body.collectFocusable(out);
        }
    }
}
