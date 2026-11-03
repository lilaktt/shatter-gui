package net.shattergui.platform;

import java.util.List;

/**
 * The mod's key bindings. Core defines them and what they do ({@code net.shattergui.ModKeys}); the platform registers
 * them with the game (unbound by default, in the mod's own category of the controls) and reports the presses.
 */
public interface KeyBindings {
    /** A binding: its translation key and what a press does. */
    record Binding(String id, Runnable onPress) {
    }

    /**
     * Registers the bindings in this order (the controls list shows them so). Each client tick the presses are handled
     * binding by binding in {@code pollOrder}, every press of one before the next.
     */
    void register(List<Binding> bindings, List<Binding> pollOrder);
}
