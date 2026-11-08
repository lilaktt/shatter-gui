package net.shattergui.platform.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.shattergui.ScreenRules;
import net.shattergui.config.ScreenCatalog;
import net.shattergui.config.screen.ScreenIcons;
import net.shattergui.platform.Desktop;
import net.shattergui.platform.GameInfo;
import net.shattergui.platform.Text;
import net.shattergui.ui.Ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The running game on 26.1 with Fabric Loader: screens, mods, the world, the clipboard. */
final class Game26 implements GameInfo, Desktop {
    /** Fabric's metadata of a mod. */
    private record Meta(ModMetadata meta) implements ModMeta {
        @Override
        public String name() {
            return meta.getName();
        }

        @Override
        public String version() {
            return meta.getVersion().getFriendlyString();
        }

        @Override
        public List<String> authors() {
            return meta.getAuthors().stream().map(Person::getName).toList();
        }

        @Override
        public Collection<String> licenses() {
            return meta.getLicense();
        }

        @Override
        public Optional<String> contact(String kind) {
            return meta.getContact().get(kind);
        }
    }

    @Override
    public Collection<ScreenEntry> screens() {
        List<ScreenEntry> out = new ArrayList<>();
        for (ScreenCatalog.Entry e : ScreenCatalog.entries()) {
            out.add(new ScreenEntry(e.key(), e.title(), e.modId(), e.className()));
        }
        return out;
    }

    @Override
    public Set<String> seenThisSession() {
        return ScreenRules.seen().keySet();
    }

    @Override
    public String modName(String modId) {
        return ScreenCatalog.modName(modId);
    }

    @Override
    public String ownerOfKey(String key) {
        return ScreenCatalog.ownerOfKey(key);
    }

    @Override
    public java.util.Map<String, String> diagnostics() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        out.put("Shatter GUI", version(net.shattergui.ShatterMod.MOD_ID));
        out.put("Build", net.shattergui.BuildInfo.describe() + ", for Minecraft " + net.shattergui.BuildInfo.minecraft());
        out.put("Minecraft", version("minecraft"));
        out.put("Fabric Loader", version("fabricloader"));
        out.put("Fabric API", version("fabric-api"));
        out.put("Iris", version("iris"));
        out.put("Sodium", version("sodium"));
        // 1.8 is "spatial-gui", later versions "spatial_gui".
        String spatial = version("spatial_gui");
        out.put("Spatial GUI", spatial.equals("not installed") ? version("spatial-gui") : spatial);
        try {
            Mc26.gpuInfo(out);
        } catch (RuntimeException e) {
            out.put("GPU", "unknown (" + e.getClass().getSimpleName() + ")");
        }
        out.put("Shader pack", net.shattergui.compat.iris.IrisCompat.describePack());
        out.put("Language", Minecraft.getInstance().options.languageCode);
        return out;
    }

    private static String version(String modId) {
        return net.shattergui.debug.Diagnostics.version(FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null));
    }

    @Override
    public ModMeta mod(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(ModContainer::getMetadata).map(Meta::new)
                .orElse(null);
    }

    @Override
    public void drawScreenIcon(Ui ui, String key, String modId, int x, int y, int size) {
        ScreenIcons.draw(ui, key, modId, x, y, size);
    }

    @Override
    public boolean inWorld() {
        return Minecraft.getInstance().level != null;
    }

    @Override
    public boolean screenOpen() {
        return Mc26.screen() != null;
    }

    @Override
    public void showScreen(Object screen) {
        Mc26.setScreen((Screen) screen);
    }

    @Override
    public void actionBar(Text message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(ComponentText.unwrap(message));
        }
    }

    // --- desktop ---

    @Override
    public String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public void openPath(Path path) {
        Mc26.openPath(path);
    }

    @Override
    public double guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }
}
