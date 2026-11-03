package net.shattergui.platform.impl;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.ShatterMod;
import net.shattergui.ShatterKeys;
import net.shattergui.compat.iris.IrisCompat;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.fly.FlyOpen;
import net.shattergui.shatter.ShardManager;
import net.shattergui.platform.ConfigPaths;
import net.shattergui.platform.Desktop;
import net.shattergui.platform.EffectCatalog;
import net.shattergui.platform.GameInfo;
import net.shattergui.platform.InputHooks;
import net.shattergui.platform.KeyBindings;
import net.shattergui.platform.ParticleSpawner;
import net.shattergui.platform.Platform;
import net.shattergui.platform.ScreenCapture;
import net.shattergui.platform.ShaderCompat;
import net.shattergui.platform.ShardRenderer;
import net.shattergui.platform.SoundPlayer;
import net.shattergui.platform.SpatialGuiBridge;
import net.shattergui.platform.Text;
import net.shattergui.platform.Texts;
import net.shattergui.platform.UiTexture;
import net.shattergui.platform.UiTextures;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Minecraft 1.21.1 with Fabric. Found by core through {@code META-INF/services}. The services only forward to the
 * classes that do the work; nothing here decides anything.
 */
public final class PlatformImpl implements Platform {
    private final ConfigPaths paths = () -> FabricLoader.getInstance().getConfigDir();
    private final Texts121 texts = new Texts121();
    private final UiTextures textures = new Textures();
    private final SoundPlayer sounds = new Registries121.Sounds();
    private final ParticleSpawner particles = new Registries121.Particles();
    private final ScreenCapture capture = new Capture121();
    private final ShardRenderer shards = ShardManager::clear;
    private final InputHooks input = FlyOpen::stop;
    private final SpatialGuiBridge spatialGui = SpatialGuiCompat::cancelPending;
    private final ShaderCompat shaders = IrisCompat::shaderPackInUse;
    private final EffectCatalog effects = new Effects121();
    private final Game121 game = new Game121();

    @Override
    public ConfigPaths paths() {
        return paths;
    }

    @Override
    public Texts texts() {
        return texts;
    }

    @Override
    public UiTextures textures() {
        return textures;
    }

    @Override
    public SoundPlayer sounds() {
        return sounds;
    }

    @Override
    public ParticleSpawner particles() {
        return particles;
    }

    @Override
    public KeyBindings keys() {
        return ShatterKeys.BINDINGS;
    }

    @Override
    public ScreenCapture capture() {
        return capture;
    }

    @Override
    public ShardRenderer shards() {
        return shards;
    }

    @Override
    public InputHooks input() {
        return input;
    }

    @Override
    public SpatialGuiBridge spatialGui() {
        return spatialGui;
    }

    @Override
    public ShaderCompat shaders() {
        return shaders;
    }

    @Override
    public EffectCatalog effects() {
        return effects;
    }

    @Override
    public GameInfo game() {
        return game;
    }

    @Override
    public Desktop desktop() {
        return game;
    }

    /** 1.21.1's pictures hold ABGR pixels (26.1's take ARGB): the red and blue bytes swap. */
    private static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | ((color >> 16) & 0xFF) | ((color & 0xFF) << 16);
    }

    private static final class Textures implements UiTextures {
        private record Image121(NativeImage image) implements Image {
            @Override
            public int width() {
                return image.getWidth();
            }

            @Override
            public int height() {
                return image.getHeight();
            }

            @Override
            public int getPixel(int x, int y) {
                return swapRedBlue(image.getPixelRGBA(x, y));
            }

            @Override
            public void close() {
                image.close();
            }
        }

        @Override
        public UiTexture create(String path, String label, int width, int height, Consumer<Canvas> paint) {
            NativeImage image = new NativeImage(width, height, false);
            try {
                paint.accept(new Canvas() {
                    @Override
                    public int width() {
                        return image.getWidth();
                    }

                    @Override
                    public int height() {
                        return image.getHeight();
                    }

                    @Override
                    public void set(int x, int y, int argb) {
                        image.setPixelRGBA(x, y, swapRedBlue(argb));
                    }
                });
            } catch (RuntimeException e) {
                image.close();
                throw e;
            }
            return register(path, new DynamicTexture(image));
        }

        @Override
        public Image decodePng(byte[] png) throws IOException {
            return new Image121(NativeImage.read(png));
        }

        @Override
        public UiTexture upload(String path, String label, Image image) {
            return register(path, new DynamicTexture(((Image121) image).image()));
        }

        private static UiTexture register(String path, DynamicTexture texture) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ShatterMod.MOD_ID, path);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return new Texture121.Registered(texture, id);
        }

        @Override
        public void release(UiTexture texture) {
            Minecraft.getInstance().getTextureManager().release(((Texture121) texture).id());
        }
    }

}
