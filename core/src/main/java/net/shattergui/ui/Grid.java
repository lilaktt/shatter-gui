package net.shattergui.ui;

public class Grid extends Container {
    private final int minCell;
    private final int gap;

    public Grid(int minCell, int gap) {
        this.minCell = minCell;
        this.gap = gap;
    }

    public int columns(int width) {
        return Math.max(1, Math.min(Math.max(1, children.size()), (width + gap) / (minCell + gap)));
    }

    @Override
    protected int measure(int width) {
        int cols = columns(width);
        int cell = Math.min(minCell * 2, (width - gap * (cols - 1)) / cols);
        int cy = y;
        int rowH = 0;
        int col = 0;
        int rowStart = 0;
        for (int i = 0; i < children.size(); i++) {
            Widget child = children.get(i);
            child.layout(x + col * (cell + gap), cy, cell);
            rowH = Math.max(rowH, child.h);
            col++;
            if (col == cols || i == children.size() - 1) {
                for (int j = rowStart; j <= i; j++) {
                    Widget c = children.get(j);
                    c.setBounds(c.x, c.y, c.w, rowH);
                }
                cy += rowH + gap;
                rowH = 0;
                col = 0;
                rowStart = i + 1;
            }
        }
        return children.isEmpty() ? 0 : cy - y - gap;
    }
}
