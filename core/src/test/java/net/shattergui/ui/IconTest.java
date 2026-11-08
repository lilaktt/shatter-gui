package net.shattergui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
