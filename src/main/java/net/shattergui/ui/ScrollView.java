package net.shattergui.ui;

import java.util.List;

/**
 * A clipped, vertically scrolling view of one content widget. The wheel adds speed that decays over time
 * ({@link Theme#SCROLL_FRICTION}), so scrolling glides; the scrollbar thumb can be dragged; focus moving to a widget
 * inside scrolls it into sight ({@link #reveal}). Soft shadows at the edges show there is more.
 */
public class ScrollView extends Widget {
    private static final int BAR = 5;
    private final Widget content;
    private float offset;
    private float velocity;
    private boolean draggingBar;
    private double dragStartY;
    private float dragStartOffset;
    private final Anim barHover = new Anim(0f, Theme.SPEED_HOVER);

    public ScrollView(Widget content) {
        this.content = content;
        content.parent = this;
    }

    public Widget content() {
        return content;
    }

    public void scrollToTop() {
        offset = 0;
        velocity = 0;
    }

    /** Jumps to the end (clamped at the next layout). */
    public void scrollToEnd() {
        offset = Float.MAX_VALUE / 4;
        velocity = 0;
    }

    private int maxScroll() {
        return Math.max(0, content.h - h);
    }

    @Override
    protected void arrange() {
        offset = Math.clamp(offset, 0, maxScroll());
        content.layout(x, y - Math.round(offset), w - BAR - 2);
        offset = Math.clamp(offset, 0, maxScroll());
    }

    @Override
    protected void draw(Ui ui) {
        float dt = UiClock.dt();
        if (Math.abs(velocity) > 0.5f) {
            offset += velocity * dt;
            velocity *= (float) Math.pow(Theme.SCROLL_FRICTION, dt);
            if (offset < 0 || offset > maxScroll()) {
                offset = Math.clamp(offset, 0, maxScroll());
                velocity = 0;
            }
        } else {
            velocity = 0;
        }
        ui.pushClip(x, y, w, h);
        content.render(ui);
        // Fades at the edges when there is more above or below.
        if (offset > 1) {
            ui.g.fillGradient(x, y, x + w - BAR - 2, y + 8, 0x60000000, 0x00000000);
        }
        if (offset < maxScroll() - 1) {
            ui.g.fillGradient(x, y + h - 8, x + w - BAR - 2, y + h, 0x00000000, 0x60000000);
        }
        ui.popClip();
        int max = maxScroll();
        if (max > 0) {
            boolean over = ui.mouseX >= x + w - BAR - 2 && ui.mouseX < x + w && ui.mouseY >= y && ui.mouseY < y + h;
            float hv = barHover.target(over || draggingBar).tick();
            int barH = Math.max(14, h * h / Math.max(1, content.h));
            int barY = y + Math.round((h - barH) * (offset / max));
            Draw.roundRect(ui, x + w - BAR, y, BAR, h, 2, Theme.fade(Theme.CARD, 0.6f));
            Draw.roundRect(ui, x + w - BAR, barY, BAR, barH, 2, Theme.mix(Theme.BORDER_STRONG, Theme.accent(), hv));
        }
    }

    @Override
    public Widget hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        if (mx >= x + w - BAR - 2 && maxScroll() > 0) {
            return this;
        }
        Widget hit = content.hit(mx, my);
        return hit != null ? hit : this;
    }

    @Override
    public void collectFocusable(List<Widget> out) {
        if (visible) {
            content.collectFocusable(out);
        }
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (maxScroll() <= 0) {
            return false;
        }
        velocity = Math.clamp(velocity - (float) amount * 260f, -2400f, 2400f);
        return true;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button == 0 && mx >= x + w - BAR - 2 && maxScroll() > 0) {
            draggingBar = true;
            dragStartY = my;
            dragStartOffset = offset;
            velocity = 0;
            int barH = Math.max(14, h * h / Math.max(1, content.h));
            int barY = y + Math.round((h - barH) * (offset / maxScroll()));
            if (my < barY || my > barY + barH) {
                // Jump so the thumb is centred under the mouse.
                offset = (float) Math.clamp((my - y - barH / 2.0) / Math.max(1, h - barH) * maxScroll(), 0, maxScroll());
                dragStartOffset = offset;
            }
            return true;
        }
        return false;
    }

    @Override
    public void mouseDrag(double mx, double my) {
        if (draggingBar) {
            int barH = Math.max(14, h * h / Math.max(1, content.h));
            float perPixel = (float) maxScroll() / Math.max(1, h - barH);
            offset = (float) Math.clamp(dragStartOffset + (my - dragStartY) * perPixel, 0, maxScroll());
        }
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        draggingBar = false;
    }

    /** Scrolls so the widget is fully visible. */
    public void reveal(Widget widget) {
        if (widget.y < y + 2) {
            offset -= (y + 2) - widget.y;
        } else if (widget.y + widget.h > y + h - 2) {
            offset += widget.y + widget.h - (y + h - 2);
        }
        offset = Math.clamp(offset, 0, maxScroll());
        velocity = 0;
    }
}
