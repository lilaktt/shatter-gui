package net.shattergui.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Children side by side, vertically centred. Each child has a fixed width or a share of the rest ({@link #flex});
 * when the row is narrower than {@link #wrapBelow}, children stack instead (for small GUI scales).
 */
public class Row extends Container {
    private final List<Integer> widths = new ArrayList<>();
    private final int gap;
    private int wrapBelow;

    public Row(int gap) {
        this.gap = gap;
    }

    /** Adds a child of fixed width. */
    public <T extends Widget> T fixed(T child, int width) {
        widths.add(width);
        return add(child);
    }

    /** Adds a child taking a share ({@code weight}) of the width left over. */
    public <T extends Widget> T flex(T child, int weight) {
        widths.add(-weight);
        return add(child);
    }

    /** Below this width the children are stacked. */
    public Row wrapBelow(int width) {
        wrapBelow = width;
        return this;
    }

    @Override
    public void clear() {
        super.clear();
        widths.clear();
    }

    @Override
    protected int measure(int width) {
        if (width < wrapBelow) {
            int cy = y;
            for (Widget child : children) {
                if (child.visible) {
                    child.layout(x, cy, width);
                    cy += child.h + gap;
                }
            }
            return Math.max(0, cy - y - gap);
        }
        int fixed = 0;
        int weights = 0;
        int count = 0;
        for (int i = 0; i < children.size(); i++) {
            if (!children.get(i).visible) {
                continue;
            }
            int spec = widths.get(i);
            if (spec >= 0) {
                fixed += spec;
            } else {
                weights -= spec;
            }
            count++;
        }
        int free = Math.max(0, width - fixed - gap * Math.max(0, count - 1));
        int cx = x;
        int height = 0;
        int[] cw = new int[children.size()];
        int lastFlex = -1;
        int used = 0;
        for (int i = 0; i < children.size(); i++) {
            Widget child = children.get(i);
            if (!child.visible) {
                continue;
            }
            int spec = widths.get(i);
            cw[i] = spec >= 0 ? spec : weights == 0 ? 0 : free * -spec / weights;
            if (spec < 0) {
                lastFlex = i;
                used += cw[i];
            }
        }
        if (lastFlex >= 0) {
            cw[lastFlex] += free - used;
        }
        for (int i = 0; i < children.size(); i++) {
            Widget child = children.get(i);
            if (!child.visible) {
                continue;
            }
            child.layout(cx, y, cw[i]);
            height = Math.max(height, child.h);
            cx += cw[i] + gap;
        }
        for (Widget child : children) {
            if (child.visible && child.h < height) {
                child.layout(child.x, y + (height - child.h) / 2, child.w);
            }
        }
        return height;
    }
}
