package net.shattergui.platform;

import java.nio.file.Path;

public interface Desktop {
    String clipboard();

    void setClipboard(String text);

    void openPath(Path path);

    double guiScale();
}
