package net.shattergui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.shattergui.config.ShatterConfig;
import net.shattergui.platform.KeyBindings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's key bindings on 26.1 ({@link ModKeys} defines them and what they do): registered with Fabric in the mod's
 * own category, unbound by default; presses are handled at the end of each client tick.
 */
public final class ShatterKeys implements KeyBindings {
    public static final ShatterKeys BINDINGS = new ShatterKeys();
    private static final Map<String, KeyMapping> MAPPINGS = new LinkedHashMap<>();
    private static List<Binding> pollOrder = List.of();

    private ShatterKeys() {
    }

    @Override
    public void register(List<Binding> bindings, List<Binding> pollOrder) {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "main"));
        for (Binding binding : bindings) {
            MAPPINGS.put(binding.id(), KeyMappingHelper.registerKeyMapping(new KeyMapping(binding.id(),
                    InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category)));
        }
        ShatterKeys.pollOrder = List.copyOf(pollOrder);
    }

    public static void tick(Minecraft mc) {
        for (Binding binding : pollOrder) {
            KeyMapping mapping = MAPPINGS.get(binding.id());
            while (mapping.consumeClick()) {
                binding.onPress().run();
            }
        }
    }

    /** See {@link ModKeys#nextFavorite}. */
    public static String nextFavorite(ShatterConfig config) {
        return ModKeys.nextFavorite(config);
    }

    /** The cycle key (tests bind it). */
    public static KeyMapping cycleKey() {
        return MAPPINGS.get(ModKeys.CYCLE_EFFECT);
    }

    /** See {@link ModKeys#setEnabled}. */
    public static void setEnabled(boolean enabled) {
        ModKeys.setEnabled(enabled);
    }
}
