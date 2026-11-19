package net.shattergui.platform;

import net.shattergui.ui.Ui;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GameInfo {
    record ScreenEntry(String key, String title, String modId, String className) {
    }

    interface ModMeta {
        String name();

        String version();

        List<String> authors();

        Collection<String> licenses();

        Optional<String> contact(String kind);
    }

    Collection<ScreenEntry> screens();

    Set<String> seenThisSession();

    String modName(String modId);

    String ownerOfKey(String key);

    ModMeta mod(String modId);

    void drawScreenIcon(Ui ui, String key, String modId, int x, int y, int size);

    boolean inWorld();

    boolean screenOpen();

    void showScreen(Object screen);

    java.util.Map<String, String> diagnostics();

    void actionBar(Text message);
}
