package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;

/** What the scenarios need from the game and differs between 26.x versions; see {@code Mc26} for the game code. */
final class McTest {
    private McTest() {
    }

    static Overlay overlay(Minecraft mc) {
        return mc.getOverlay();
    }

    static void setHudHidden(Minecraft mc, boolean hidden) {
        mc.options.hideGui = hidden;
    }

    static long guiTicks(Minecraft mc) {
        return mc.gui.getGuiTicks();
    }

    static void waitForChunksRender(TestSingleplayerContext singleplayer) {
        singleplayer.getClientLevel().waitForChunksRender();
    }
}
