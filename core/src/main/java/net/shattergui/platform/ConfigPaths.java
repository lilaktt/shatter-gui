package net.shattergui.platform;

import java.nio.file.Path;

/** Where the mod keeps its files. */
public interface ConfigPaths {
    /** The game's config folder (the mod writes {@code shatter_gui.json} and {@code shatter_gui/} there). */
    Path configDir();
}
