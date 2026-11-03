package net.shattergui.platform.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.shattergui.platform.Text;

/** Core's {@link Text} as the game's {@link Component} (the same API in 1.21.1 as in 26.1). */
public final class ComponentText implements Text {
    private final MutableComponent component;

    private ComponentText(MutableComponent component) {
        this.component = component;
    }

    public static ComponentText of(Component component) {
        return new ComponentText(component instanceof MutableComponent m ? m : component.copy());
    }

    public static Component unwrap(Text text) {
        return text == null ? null : ((ComponentText) text).component;
    }

    static ComponentText translatable(String key, Object... args) {
        if (args.length == 0) {
            return new ComponentText(Component.translatable(key));
        }
        Object[] own = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            own[i] = args[i] instanceof ComponentText t ? t.component : args[i];
        }
        return new ComponentText(Component.translatable(key, own));
    }

    static ComponentText literal(String text) {
        return new ComponentText(Component.literal(text));
    }

    static ComponentText empty() {
        return new ComponentText(Component.empty());
    }

    @Override
    public String getString() {
        return component.getString();
    }

    @Override
    public Text copy() {
        return new ComponentText(component.copy());
    }

    @Override
    public Text append(String text) {
        component.append(text);
        return this;
    }

    @Override
    public Text append(Text text) {
        component.append(unwrap(text));
        return this;
    }

    @Override
    public Text bold() {
        component.withStyle(ChatFormatting.BOLD);
        return this;
    }

    @Override
    public String translationKey() {
        return component.getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ComponentText other && component.equals(other.component);
    }

    @Override
    public int hashCode() {
        return component.hashCode();
    }

    @Override
    public String toString() {
        return component.toString();
    }
}
