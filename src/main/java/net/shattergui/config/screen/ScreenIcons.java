package net.shattergui.config.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ScreenCatalog;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The picture next to a screen in the editor: for vanilla menus and screens an item that stands for it (furnace,
 * anvil, a book, a sign…), for screens of other mods the mod's icon from its Fabric metadata, otherwise a plain window.
 */
final class ScreenIcons {
    private static final Map<String, Item> VANILLA = new HashMap<>();
    /** Mod id → its icon texture, or null if it has none (also cached). */
    private static final Map<String, AbstractTexture> MOD_ICONS = new HashMap<>();

    static {
        for (String size : new String[]{"9x1", "9x2", "9x3", "9x4", "9x5", "9x6"}) {
            VANILLA.put("minecraft:generic_" + size, Items.CHEST);
        }
        VANILLA.put("minecraft:generic_3x3", Items.DISPENSER);
        VANILLA.put("minecraft:crafter_3x3", Items.CRAFTER);
        VANILLA.put("minecraft:anvil", Items.ANVIL);
        VANILLA.put("minecraft:beacon", Items.BEACON);
        VANILLA.put("minecraft:blast_furnace", Items.BLAST_FURNACE);
        VANILLA.put("minecraft:brewing_stand", Items.BREWING_STAND);
        VANILLA.put("minecraft:crafting", Items.CRAFTING_TABLE);
        VANILLA.put("minecraft:enchantment", Items.ENCHANTING_TABLE);
        VANILLA.put("minecraft:furnace", Items.FURNACE);
        VANILLA.put("minecraft:grindstone", Items.GRINDSTONE);
        VANILLA.put("minecraft:hopper", Items.HOPPER);
        VANILLA.put("minecraft:lectern", Items.LECTERN);
        VANILLA.put("minecraft:loom", Items.LOOM);
        VANILLA.put("minecraft:merchant", Items.EMERALD);
        VANILLA.put("minecraft:shulker_box", Items.SHULKER_BOX);
        VANILLA.put("minecraft:smithing", Items.SMITHING_TABLE);
        VANILLA.put("minecraft:smoker", Items.SMOKER);
        VANILLA.put("minecraft:cartography_table", Items.CARTOGRAPHY_TABLE);
        VANILLA.put("minecraft:stonecutter", Items.STONECUTTER);
        VANILLA.put("minecraft:inventory", Items.BUNDLE);
        // Screens without a menu, by simple class name.
        VANILLA.put("ChatScreen", Items.PAPER);
        VANILLA.put("InBedChatScreen", Items.RED_BED);
        VANILLA.put("PauseScreen", Items.COMPASS);
        VANILLA.put("BookViewScreen", Items.WRITTEN_BOOK);
        VANILLA.put("BookEditScreen", Items.WRITABLE_BOOK);
        VANILLA.put("BookSignScreen", Items.WRITABLE_BOOK);
        VANILLA.put("SignEditScreen", Items.OAK_SIGN);
        VANILLA.put("HangingSignEditScreen", Items.OAK_HANGING_SIGN);
        VANILLA.put("CommandBlockEditScreen", Items.COMMAND_BLOCK);
        VANILLA.put("MinecartCommandBlockEditScreen", Items.COMMAND_BLOCK);
        VANILLA.put("StructureBlockEditScreen", Items.STRUCTURE_BLOCK);
        VANILLA.put("JigsawBlockEditScreen", Items.JIGSAW);
        VANILLA.put("HorseInventoryScreen", Items.SADDLE);
        VANILLA.put("AdvancementsScreen", Items.KNOWLEDGE_BOOK);
        VANILLA.put("StatsScreen", Items.PAPER);
        VANILLA.put("OptionsScreen", Items.COMPARATOR);
    }

    private ScreenIcons() {
    }

    /** Draws the icon of a screen key at (x, y), {@code size} pixels square. */
    static void draw(Ui ui, String key, String modId, int x, int y, int size) {
        Item item = VANILLA.get(key);
        if (item == null) {
            int dot = key.lastIndexOf('.');
            item = VANILLA.get(dot >= 0 ? key.substring(dot + 1) : key);
        }
        if (item != null && ScreenCatalog.MINECRAFT.equals(modId)) {
            float s = size / 16f;
            ui.g.pose().pushMatrix();
            ui.g.pose().translate(x, y);
            ui.g.pose().scale(s, s);
            ui.g.item(new ItemStack(item), 0, 0);
            ui.g.pose().popMatrix();
            return;
        }
        AbstractTexture icon = modIcon(modId);
        if (icon != null) {
            Draw.texture(ui, icon, x, y, size, size, 0xFFFFFFFF);
            return;
        }
        Draw.roundRect(ui, x, y, size, size, 3, Theme.RAISED);
        Draw.iconCentered(ui, Icon.WINDOW, x, y, size, size, Theme.TEXT_DIM);
    }

    /** The icon of a mod from its metadata ("icon" in fabric.mod.json), loaded once. */
    static AbstractTexture modIcon(String modId) {
        if (modId == null || ScreenCatalog.MINECRAFT.equals(modId)) {
            return null;
        }
        if (MOD_ICONS.containsKey(modId)) {
            return MOD_ICONS.get(modId);
        }
        AbstractTexture texture = null;
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        if (mod.isPresent()) {
            Optional<String> path = mod.get().getMetadata().getIconPath(32);
            Optional<Path> file = path.flatMap(p -> mod.get().findPath(p));
            if (file.isPresent()) {
                try (InputStream in = Files.newInputStream(file.get())) {
                    NativeImage image = NativeImage.read(in);
                    texture = new DynamicTexture(() -> "shatter_gui mod icon " + modId, image);
                    Identifier id = Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID,
                            "editor/modicon/" + modId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                } catch (Exception e) {
                    ShatterGuiClient.LOGGER.debug("No icon for mod {}: {}", modId, e.toString());
                    texture = null;
                }
            }
        }
        MOD_ICONS.put(modId, texture);
        return texture;
    }
}
