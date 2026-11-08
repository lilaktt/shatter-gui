package net.shattergui.ui;

import net.shattergui.ui.links.LinkButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContrastTest {
    @Test
    void ratioMatchesKnownValues() {
        assertEquals(21.0, Contrast.ratio(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, Contrast.ratio(0xFF777777, 0xFF777777), 0.0001);
        assertEquals(4.54, Contrast.ratio(0xFF767676, 0xFFFFFFFF), 0.01);
    }

    @Test
    void everyFillOnTheWayIsReadable() {
        for (LinkButton.Brand brand : LinkButton.Brand.values()) {
            for (int i = 0; i <= 100; i++) {
                float t = i / 100f;
                for (float hover : new float[]{0f, t}) {
                    int[] colors = Button.brandColors(brand.color, hover, t, 0f);
                    double ratio = Contrast.ratio(colors[1], colors[0]);
                    assertTrue(ratio >= Contrast.READABLE, String.format("%s at %.2f (hover %.2f): text %08X on %08X is %.2f:1",
                            brand, t, hover, colors[1], colors[0], ratio));
                }
            }
        }
    }

    @Test
    void fullyFilledTakesThePlatformColour() {
        for (LinkButton.Brand brand : LinkButton.Brand.values()) {
            int[] colors = Button.brandColors(brand.color, 1f, 1f, 0f);
            assertEquals(brand.color, colors[0], brand.name());
        }
        assertEquals(Contrast.DARK_TEXT, Button.brandColors(LinkButton.Brand.MODRINTH.color, 1f, 1f, 0f)[1]);
        assertEquals(Contrast.DARK_TEXT, Button.brandColors(LinkButton.Brand.GITHUB.color, 1f, 1f, 0f)[1]);
        assertEquals(Theme.TEXT, Button.brandColors(LinkButton.Brand.GITHUB.color, 0f, 0f, 0f)[1]);
    }
}
