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

public final class Mc26 {
    public static final double DEPTH_FAR = 1.0;
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

    public static int uiKey(int gameKey) {
        return gameKey;
    }

    public static int uiMouseButton(int gameButton) {
        return gameButton;
    }

    public static void gpuInfo(Map<String, String> out) {
        GpuDevice device = RenderSystem.getDevice();
        out.put("GPU", device.getRenderer());
        out.put("Driver", device.getVersion());
        out.put("GPU vendor", device.getVendor());
        out.put("Graphics backend", device.getBackendName());
    }

    public static org.joml.Matrix4f behindHand(org.joml.Matrix4fc projection, boolean shaderHand) {
        return new org.joml.Matrix4f().m22(shaderHand ? 0.005f : 0.0005f).m32(shaderHand ? 0.135f : 0.9985f).mul(projection);
    }
}
