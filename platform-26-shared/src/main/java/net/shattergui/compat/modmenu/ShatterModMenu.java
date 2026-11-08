package net.shattergui.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.shattergui.config.screen.ShatterConfigScreen;

public final class ShatterModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShatterConfigScreen::new;
    }
}
