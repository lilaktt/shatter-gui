package net.shattergui.platform;

import net.shattergui.ui.Ui;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The running game as the settings and the key actions see it: screens, installed mods, the world, the action bar. */
public interface GameInfo {
    /** A screen the mod has seen: its key (menu id or class name), first title, owning mod and class. */
    record ScreenEntry(String key, String title, String modId, String className) {
    }

    /** A mod's metadata (the settings' "About"): contact links by kind ("sources", "issues", "modrinth" …). */
    interface ModMeta {
        String name();

        String version();

        List<String> authors();

        Collection<String> licenses();

        Optional<String> contact(String kind);
    }

    /** Every screen seen in any session (kept in the config folder). */
    Collection<ScreenEntry> screens();

    /** Keys of the screens seen in this session. */
    Set<String> seenThisSession();

    /** The display name of a mod id ("Minecraft" for vanilla), or the id itself if no such mod is loaded. */
    String modName(String modId);

    /** The mod a screen key belongs to. */
    String ownerOfKey(String key);

    /** The installed mod's metadata, or null. */
    ModMeta mod(String modId);

    /** The icon of a screen (its block for vanilla containers, the mod's icon, or a window) at (x, y), size² pixels. */
    void drawScreenIcon(Ui ui, String key, String modId, int x, int y, int size);

    /** A world is loaded. */
    boolean inWorld();

    /** A screen is open. */
    boolean screenOpen();

    /** Shows a screen made in core ({@link PlatformScreen}). */
    void showScreen(Object screen);

    /** A message in the action bar (nothing without a player). */
    void actionBar(Text message);
}
