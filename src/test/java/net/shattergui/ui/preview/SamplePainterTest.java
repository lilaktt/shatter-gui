package net.shattergui.ui.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The sample interfaces fit their image exactly, and drawing outside an image is cut, never an error. */
class SamplePainterTest {
    private static SamplePainter.ArrayCanvas canvas() {
        return new SamplePainter.ArrayCanvas(SamplePainter.WIDTH, SamplePainter.HEIGHT);
    }

    @Test
    void everySampleFitsWithoutClipping() {
        for (SampleImage.Kind kind : SampleImage.Kind.values()) {
            SamplePainter painter = new SamplePainter(canvas());
            painter.paint(kind);
            assertEquals(0, painter.clipped(), kind + " draws outside the 176×166 image");
        }
    }

    @Test
    void chestLooksRight() {
        SamplePainter.ArrayCanvas c = canvas();
        new SamplePainter(c).paint(SampleImage.Kind.CHEST);
        int w = c.width;
        // Rounded corners stay transparent, the outline is black, the panel grey.
        assertEquals(0, c.pixels[0]);
        assertEquals(0xFF000000, c.pixels[3]);
        assertEquals(0xFFC6C6C6, c.pixels[40 * w + 4]);
        // 3×9 chest slots from (7, 17): slot fill grey, dark top-left edge, light bottom-right edge.
        assertEquals(0xFF8B8B8B, c.pixels[(17 + 9) * w + 7 + 18 * 8 + 9]);
        assertEquals(0xFF373737, c.pixels[17 * w + 7 + 5]);
        assertEquals(0xFFFFFFFF, c.pixels[(17 + 17) * w + 7 + 5]);
        // Hotbar row at y 141..158, last slot ends inside the panel.
        assertEquals(0xFFFFFFFF, c.pixels[(141 + 5) * w + 7 + 18 * 8 + 17]);
    }

    @Test
    void outsideRectanglesAreCut() {
        SamplePainter.ArrayCanvas c = new SamplePainter.ArrayCanvas(10, 6);
        SamplePainter p = new SamplePainter(c);
        // Partly outside on every side: cut to the canvas.
        p.fill(-3, -2, 6, 4, 1);
        assertEquals(1, c.pixels[0]);
        assertEquals(1, c.pixels[1 * 10 + 2]);
        assertEquals(0, c.pixels[2 * 10]);
        p.fill(8, 4, 5, 5, 2);
        assertEquals(2, c.pixels[5 * 10 + 9]);
        assertEquals(2, p.clipped());
        // The crash case: a 26-wide slot starting at x = 151 of 176 (scaled down: 8 wide at x = 5 of 10).
        p.fill(5, 0, 8, 1, 3);
        assertEquals(3, c.pixels[9]);
        // Entirely outside, empty or negative sizes: nothing drawn, nothing thrown.
        int[] before = c.pixels.clone();
        p.fill(50, 50, 4, 4, 9);
        p.fill(-20, 0, 5, 5, 9);
        p.fill(2, 2, 0, 3, 9);
        p.fill(2, 2, -4, 3, 9);
        p.fill(Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE, 2, 9);
        p.fill(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 9);
        assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(c.pixels));
        // An empty canvas takes anything.
        new SamplePainter(new SamplePainter.ArrayCanvas(0, 0)).paint(SampleImage.Kind.INVENTORY);
    }
}
