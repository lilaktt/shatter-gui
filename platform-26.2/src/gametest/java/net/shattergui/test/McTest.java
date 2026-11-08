package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;

final class McTest {
    private McTest() {
    }

    static net.minecraft.client.renderer.state.GameRenderState renderState(Minecraft mc) {
        return mc.gameRenderer.gameRenderState();
    }

    static Overlay overlay(Minecraft mc) {
        return mc.gui.overlay();
    }

    static void setHudHidden(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    static long guiTicks(Minecraft mc) {
        return mc.gui.hud.getGuiTicks();
    }

    static void waitForChunksRender(TestSingleplayerContext singleplayer) {
        singleplayer.getConnection().waitForChunksRender();
    }
}
