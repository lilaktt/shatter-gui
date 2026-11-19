package net.shattergui.platform.impl;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.shattergui.platform.UiTexture;

public interface Texture26 extends UiTexture {
    AbstractTexture texture();

    Identifier id();

    record Registered(AbstractTexture texture, Identifier id) implements Texture26 {
    }
}
