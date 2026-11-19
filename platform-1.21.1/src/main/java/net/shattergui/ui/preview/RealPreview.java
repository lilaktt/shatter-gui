package net.shattergui.ui.preview;

import net.minecraft.client.gui.screens.Screen;

public final class RealPreview {
    private RealPreview() {
    }

    public static boolean enabled() {
        return false;
    }

    public static void setConfigured(boolean value) {
    }

    public static void toggleQuick() {
    }

    public static boolean available() {
        return false;
    }

    public static boolean notice() {
        return false;
    }

    public static SampleImage.Image image(SampleImage.Kind kind) {
        return null;
    }

    public static void release() {
    }

    public static String describe() {
        return "not ported to 1.21.1";
    }

    public static int texturesInMemory() {
        return 0;
    }

    public static boolean blocksSetScreen(Screen screen) {
        return false;
    }

    public static void breakNextForTests() {
    }

    public static void beforeHud(float partialTick) {
    }
}
