package net.shattergui.ui;

import java.util.ArrayList;
import java.util.List;

/** A widget with children, drawn in order, hit-tested from the top. Subclasses decide the layout. */
public class Container extends Widget {
    protected final List<Widget> children = new ArrayList<>();

    public <T extends Widget> T add(T child) {
        child.parent = this;
        children.add(child);
        return child;
    }

    public void clear() {
        children.clear();
    }

    public List<Widget> children() {
        return children;
    }

    @Override
    protected void draw(Ui ui) {
        drawBackground(ui);
        for (Widget child : children) {
            child.render(ui);
        }
    }

    protected void drawBackground(Ui ui) {
    }

    @Override
    public Widget hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget hit = children.get(i).hit(mx, my);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    @Override
    public void collectFocusable(List<Widget> out) {
        if (!visible) {
            return;
        }
        super.collectFocusable(out);
        for (Widget child : children) {
            child.collectFocusable(out);
        }
    }
}
