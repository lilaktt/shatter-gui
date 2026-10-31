package net.shattergui.ui;

/** Children stacked top to bottom at full width, with padding and a gap. Invisible children take no space. */
public class Column extends Container {
    private final int pad;
    private final int gap;

    public Column(int pad, int gap) {
        this.pad = pad;
        this.gap = gap;
    }

    @Override
    protected int measure(int width) {
        int cy = y + pad;
        boolean first = true;
        for (Widget child : children) {
            if (!child.visible) {
                continue;
            }
            if (!first) {
                cy += gap;
            }
            child.layout(x + pad, cy, width - pad * 2);
            cy += child.h;
            first = false;
        }
        return cy - y + pad;
    }
}
