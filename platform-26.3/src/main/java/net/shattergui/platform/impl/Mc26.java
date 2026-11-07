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

/** The game as 26.3 offers it: see the 26.1 class of this name. GPU classes are under {@code com.mojang.renderpearl}. */
public final class Mc26 {
    /** The depth buffer's clear value (far plane): reversed-Z. */
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
        Blaze3D.openPath(path);
    }

    public static void confirmLink(Screen parent, String url) {
        ConfirmLinkScreen.confirmLinkNow(parent, URI.create(url));
    }

    /**
     * A key code of the game as core's {@code Keys} has it. 26.3 runs on SDL: its key codes are scancodes, not GLFW's.
     * Keys core does not know are -1, not passed on: scancode 32 is the digit 3, and 32 is Space in core's codes.
     */
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

    /** A mouse button of the game as core has it (0 left, 1 right, 2 middle). SDL: 1 left, 2 middle, 3 right. */
    public static int uiMouseButton(int gameButton) {
        return switch (gameButton) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> gameButton;
        };
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
