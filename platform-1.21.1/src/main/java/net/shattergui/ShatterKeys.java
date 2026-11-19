package net.shattergui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.shattergui.config.ShatterConfig;
import net.shattergui.platform.KeyBindings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShatterKeys implements KeyBindings {
    public static final ShatterKeys BINDINGS = new ShatterKeys();
    private static final Map<String, KeyMapping> MAPPINGS = new LinkedHashMap<>();
    private static List<Binding> pollOrder = List.of();

    private ShatterKeys() {
    }

    @Override
    public void register(List<Binding> bindings, List<Binding> pollOrder) {
        String category = "key.category." + ShatterGuiClient.MOD_ID + ".main";
        for (Binding binding : bindings) {
            MAPPINGS.put(binding.id(), KeyBindingHelper.registerKeyBinding(new KeyMapping(binding.id(),
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

    public static String nextFavorite(ShatterConfig config) {
        return ModKeys.nextFavorite(config);
    }

    public static KeyMapping cycleKey() {
        return MAPPINGS.get(ModKeys.CYCLE_EFFECT);
    }

    public static void setEnabled(boolean enabled) {
        ModKeys.setEnabled(enabled);
    }
}
