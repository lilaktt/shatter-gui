package net.shattergui.platform;

public interface GuiDrawing {
    void fill(int x0, int y0, int x1, int y1, int color);

    void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom);

    void outline(int x, int y, int w, int h, int color);

    void text(UiFont font, Text text, int x, int y, int color, boolean shadow);

    void text(UiFont font, String text, int x, int y, int color, boolean shadow);

    void text(UiFont font, TextLine line, int x, int y, int color, boolean shadow);

    void item(UiItem item, int x, int y);

    UiPose pose();

    void enableScissor(int x0, int y0, int x1, int y1);

    void disableScissor();

    void nextStratum();

    void triangles(UiTexture texture, boolean premultiplied, float[] data, int[] colors, int vertexCount, UiRect clip);
}
