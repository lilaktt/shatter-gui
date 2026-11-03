package net.shattergui.ui.preview;

import net.minecraft.client.gui.screens.Screen;

/**
 * The editor's real preview (vanilla screens drawn into textures) on 1.21.1: not ported yet, so it is never available
 * and the editor shows its drawn samples. Same methods as on 26.1.
 */
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

    /** {@code GameRenderer#render}, before the HUD: draws a queued picture. */
    public static void beforeHud(float partialTick) {
    }
}
