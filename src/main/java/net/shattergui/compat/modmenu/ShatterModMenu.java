package net.shattergui.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.shattergui.config.screen.ShatterConfigScreen;

/**
 * Mod Menu's "Config" button. Listed as a "modmenu" entrypoint, which only Mod Menu loads, so this class (and Mod Menu's
 * API) is never touched when Mod Menu is not installed.
 */
public final class ShatterModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShatterConfigScreen::new;
    }
}
