package net.shattergui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every icon pattern is 9×9 and draws something (a broken row would fail when the enum loads). */
class IconTest {
    @Test
    void iconsLoad() {
        for (Icon icon : Icon.values()) {
            boolean any = false;
            for (int y = 0; y < Icon.SIZE; y++) {
                for (int x = 0; x < Icon.SIZE; x++) {
                    any |= icon.at(x, y);
                }
            }
            assertTrue(any, icon.name());
        }
    }
}
