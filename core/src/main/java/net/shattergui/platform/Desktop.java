package net.shattergui.platform;

import java.nio.file.Path;

/** The computer around the game: clipboard, file manager, links; and the GUI scale. */
public interface Desktop {
    String clipboard();

    void setClipboard(String text);

    /** Opens a folder or file in the system's file manager. */
    void openPath(Path path);

    /** The GUI scale (screen pixels per GUI pixel). */
    double guiScale();
}
