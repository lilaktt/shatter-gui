package net.shattergui.platform.impl;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.Map;

/** The game as 26.2 offers it: see the 26.1 class of this name. The screen lives in {@code Minecraft#gui} here. */
public final class Mc26 {
    /** The depth buffer's clear value (far plane): reversed-Z since 26.2. */
    public static final double DEPTH_FAR = 0.0;
    /** The depth test of a pipeline drawn in the world. */
    public static final CompareOp DEPTH_TEST = CompareOp.GREATER_THAN_OR_EQUAL;

    private Mc26() {
    }

    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static Camera camera() {
        return Minecraft.getInstance().gameRenderer.mainCamera();
    }

    public static Lighting lighting() {
        return Minecraft.getInstance().gameRenderer.lighting();
    }

    public static RenderTarget mainTarget() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }

    public static void clearToTransparent(CommandEncoder encoder, GpuTexture color) {
        encoder.clearColorTexture(color, new Vector4f(0f, 0f, 0f, 0f));
    }

    /** The second sampler of an entity pipeline: the overlay texture. */
    public static RenderPipeline.Builder withSampler1(RenderPipeline.Builder builder) {
        return builder.withBindGroupLayout(BindGroupLayouts.SAMPLER1);
    }

    public static int lightCoords(ClientLevel level, BlockPos pos) {
        return LightCoordsUtil.getLightCoords(level, pos);
    }

    public static Item bedIcon() {
        return Items.BED.pick(DyeColor.RED);
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
        DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
        out.put("GPU", info.name());
        out.put("Driver", info.driverInfo());
        out.put("GPU vendor", info.vendorName());
        out.put("Graphics backend", info.backendName());
    }
}
