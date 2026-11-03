package net.shattergui.platform;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Everything core needs from the game, one implementation per Minecraft version and loader (platform-* modules). Core
 * never sees Minecraft or a loader: it asks for these services. The implementation is found once, on first use, through
 * {@link ServiceLoader} ({@code META-INF/services/net.shattergui.platform.Platform} in the platform module), so it does
 * not matter which class is touched first (an entrypoint, a mixin, Mod Menu).
 */
public interface Platform {
    /** The platform of this jar. */
    static Platform get() {
        return Holder.INSTANCE;
    }

    /** Where the mod keeps its files. */
    ConfigPaths paths();

    /** Text: translations, literal text and the font. */
    Texts texts();

    /** Textures for the UI library (sample pictures, effect textures) and the PNG decoder. */
    UiTextures textures();

    /** UI and preview sounds. */
    SoundPlayer sounds();

    /** The particle registry and the player's particle setting. */
    ParticleSpawner particles();

    /** The mod's key bindings and the master switch they drive. */
    KeyBindings keys();

    /** The real screens drawn into textures for the previews, and effects played in the world from the editor. */
    ScreenCapture capture();

    /** Drawing the shards in the world (what the settings show about it). */
    ShardRenderer shards();

    /** How the game's screens and input reach the mod (what the settings show about it). */
    InputHooks input();

    /** Spatial GUI, if installed. */
    SpatialGuiBridge spatialGui();

    /** Shader packs (Iris), if installed. */
    ShaderCompat shaders();

    /** The loaded effects (built-in, data, custom) as the settings and the editor see them. */
    EffectCatalog effects();

    /** Screens seen or registered in the game, and the installed mods. */
    GameInfo game();

    /** The clipboard, opening folders, the GUI scale. */
    Desktop desktop();

    /** Lazy holder: the first {@link #get()} finds the implementation. */
    final class Holder {
        static final Platform INSTANCE = find();

        private Holder() {
        }

        private static Platform find() {
            Iterator<Platform> found = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).iterator();
            if (!found.hasNext()) {
                throw new IllegalStateException("No Shatter GUI platform in this jar (META-INF/services/"
                        + Platform.class.getName() + ")");
            }
            return found.next();
        }
    }
}
