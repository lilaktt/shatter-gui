package net.shattergui.platform.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.shattergui.platform.Keys;
import org.joml.Vector4f;

import java.net.URI;
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
        Blaze3D.openPath(path);
    }

    public static void confirmLink(Screen parent, String url) {
        ConfirmLinkScreen.confirmLinkNow(parent, URI.create(url));
    }

    public static int uiKey(int gameKey) {
        return switch (gameKey) {
            case InputConstants.KEY_SPACE -> Keys.KEY_SPACE;
            case InputConstants.KEY_S -> Keys.KEY_S;
            case InputConstants.KEY_Y -> Keys.KEY_Y;
            case InputConstants.KEY_Z -> Keys.KEY_Z;
            case InputConstants.KEY_ESCAPE -> Keys.KEY_ESCAPE;
            case InputConstants.KEY_RETURN -> Keys.KEY_RETURN;
            case InputConstants.KEY_TAB -> Keys.KEY_TAB;
            case InputConstants.KEY_BACKSPACE -> Keys.KEY_BACKSPACE;
            case InputConstants.KEY_DELETE -> Keys.KEY_DELETE;
            case InputConstants.KEY_RIGHT -> Keys.KEY_RIGHT;
            case InputConstants.KEY_LEFT -> Keys.KEY_LEFT;
            case InputConstants.KEY_DOWN -> Keys.KEY_DOWN;
            case InputConstants.KEY_UP -> Keys.KEY_UP;
            case InputConstants.KEY_HOME -> Keys.KEY_HOME;
            case InputConstants.KEY_END -> Keys.KEY_END;
            case InputConstants.KEY_F2 -> Keys.KEY_F2;
            case InputConstants.KEY_NUMPADENTER -> Keys.KEY_NUMPADENTER;
            default -> -1;
        };
    }

    public static int uiMouseButton(int gameButton) {
        return switch (gameButton) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> gameButton;
        };
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
