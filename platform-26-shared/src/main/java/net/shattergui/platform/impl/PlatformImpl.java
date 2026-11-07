package net.shattergui.platform.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.shattergui.ShatterKeys;
import net.shattergui.compat.iris.IrisCompat;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.fly.FlyOpen;
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
import net.shattergui.platform.Texts;
import net.shattergui.platform.UiTextures;
import net.shattergui.shatter.ShardManager;

/**
 * Minecraft 26.1.2 with Fabric. Found by core through {@code META-INF/services}. The services only forward to the
 * classes that did the work before core existed; nothing here decides anything.
 */
public final class PlatformImpl implements Platform {
    private final ConfigPaths paths = () -> FabricLoader.getInstance().getConfigDir();
    private final Texts26 texts = new Texts26();
    private final UiTextures textures = new UiTextures26();
    private final SoundPlayer sounds = new Registries26.Sounds();
    private final ParticleSpawner particles = new Registries26.Particles();
    private final ScreenCapture capture = new Capture26();
    private final ShardRenderer shards = ShardManager::clear;
    private final InputHooks input = FlyOpen::stop;
    private final SpatialGuiBridge spatialGui = SpatialGuiCompat::cancelPending;
    private final ShaderCompat shaders = IrisCompat::shaderPackInUse;
    private final EffectCatalog effects = new Effects26();
    private final Game26 game = new Game26();

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
}
