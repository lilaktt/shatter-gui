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

public final class Mc26 {
    public static final double DEPTH_FAR = 0.0;
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

    public static int uiKey(int gameKey) {
        return gameKey;
    }

    public static int uiMouseButton(int gameButton) {
        return gameButton;
    }

    public static void gpuInfo(Map<String, String> out) {
        DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
        out.put("GPU", info.name());
        out.put("Driver", info.driverInfo());
        out.put("GPU vendor", info.vendorName());
        out.put("Graphics backend", info.backendName());
    }

    public static org.joml.Matrix4f behindHand(org.joml.Matrix4fc projection, boolean shaderHand) {
        return new org.joml.Matrix4f().m22(shaderHand ? 0.005f : 0.0005f).m32(shaderHand ? 0.43f : 0.0005f).mul(projection);
    }
}
