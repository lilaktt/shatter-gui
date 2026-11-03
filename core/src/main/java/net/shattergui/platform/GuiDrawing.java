package net.shattergui.platform;

/**
 * Drawing one frame of a screen: the primitives the UI library is built from. Coordinates are GUI pixels, colours ARGB;
 * everything goes through the current {@link #pose()} and scissor.
 */
public interface GuiDrawing {
    void fill(int x0, int y0, int x1, int y1, int color);

    /** Vertical gradient from {@code top} to {@code bottom}. */
    void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom);

    /** A 1-pixel rectangle border. */
    void outline(int x, int y, int w, int h, int color);

    void text(UiFont font, Text text, int x, int y, int color, boolean shadow);

    void text(UiFont font, String text, int x, int y, int color, boolean shadow);

    void text(UiFont font, TextLine line, int x, int y, int color, boolean shadow);

    /** A 16×16 item icon. */
    void item(UiItem item, int x, int y);

    UiPose pose();

    void enableScissor(int x0, int y0, int x1, int y1);

    void disableScissor();

    /** What follows is drawn above everything drawn so far (overlays, tooltips). */
    void nextStratum();

    /**
     * A batch of triangles: {@code data} holds per vertex {x, y, u, v} (u, v ignored without a texture), {@code colors}
     * one ARGB per vertex; the first {@code vertexCount - vertexCount % 3} vertices are drawn (either winding), with the
     * current pose, cut to {@code clip} (null: not clipped); the area they cover is taken from all {@code vertexCount}.
     * {@code premultiplied}: the texture's colours are already multiplied by its alpha (a GUI captured on a transparent
     * target), so it is blended that way (the caller premultiplied the vertex colours). The arrays are not copied.
     */
    void triangles(UiTexture texture, boolean premultiplied, float[] data, int[] colors, int vertexCount, UiRect clip);
}
