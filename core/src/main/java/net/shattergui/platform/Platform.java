package net.shattergui.platform;

import java.util.Iterator;
import java.util.ServiceLoader;

public interface Platform {
    static Platform get() {
        return Holder.INSTANCE;
    }

    ConfigPaths paths();

    Texts texts();

    UiTextures textures();

    SoundPlayer sounds();

    ParticleSpawner particles();

    KeyBindings keys();

    ScreenCapture capture();

    ShardRenderer shards();

    InputHooks input();

    SpatialGuiBridge spatialGui();

    ShaderCompat shaders();

    EffectCatalog effects();

    GameInfo game();

    Desktop desktop();

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
