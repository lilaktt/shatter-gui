package net.shattergui.platform.impl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.Map;

/**
 * The game as this line of versions (26.1.x) offers it. {@code platform-26-shared} is compiled against every 26.x and
 * reaches what moved between them only through this class; each platform module has its own (same methods). Add a
 * method here only for something that really differs.
 */
public final class Mc26 {
    /** The depth buffer's clear value (far plane). Reversed since 26.2. */
    public static final double DEPTH_FAR = 1.0;
    /** The depth test of a pipeline drawn in the world. */
    public static final CompareOp DEPTH_TEST = CompareOp.LESS_THAN_OR_EQUAL;

    private Mc26() {
    }

    public static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static Lighting lighting() {
        return Minecraft.getInstance().gameRenderer.getLighting();
    }

    public static RenderTarget mainTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    public static void clearToTransparent(CommandEncoder encoder, GpuTexture color) {
        encoder.clearColorTexture(color, 0);
    }

    /** The second sampler of an entity pipeline: the overlay texture. */
    public static RenderPipeline.Builder withSampler1(RenderPipeline.Builder builder) {
        return builder.withSampler("Sampler1");
    }

    public static int lightCoords(ClientLevel level, BlockPos pos) {
        return LevelRenderer.getLightCoords(level, pos);
    }

    public static Item bedIcon() {
        return Items.RED_BED;
    }

    public static void openPath(Path path) {
        Util.getPlatform().openPath(path);
    }

    public static void confirmLink(Screen parent, String url) {
        ConfirmLinkScreen.confirmLinkNow(parent, url);
    }

    /** A key code of the game as core's {@code Keys} has it: the same here (GLFW codes). */
    public static int uiKey(int gameKey) {
        return gameKey;
    }

    /** A mouse button of the game as core has it (0 left, 1 right, 2 middle): the same here. */
    public static int uiMouseButton(int gameButton) {
        return gameButton;
    }

    /** What {@code debug/Diagnostics} reports about the graphics card. */
    public static void gpuInfo(Map<String, String> out) {
        GpuDevice device = RenderSystem.getDevice();
        out.put("GPU", device.getRenderer());
        out.put("Driver", device.getVersion());
        out.put("GPU vendor", device.getVendor());
        out.put("Graphics backend", device.getBackendName());
    }
}
