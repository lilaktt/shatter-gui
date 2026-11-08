package net.shattergui.platform;

import java.util.List;

public interface KeyBindings {
    record Binding(String id, Runnable onPress) {
    }

    void register(List<Binding> bindings, List<Binding> pollOrder);
}
