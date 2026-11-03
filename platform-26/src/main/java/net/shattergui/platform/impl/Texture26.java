package net.shattergui.platform.impl;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.shattergui.platform.UiTexture;

/** A UI texture on 26.1: the game's texture and the id it is registered under. */
public interface Texture26 extends UiTexture {
    AbstractTexture texture();

    Identifier id();

    /** A texture registered by {@link UiTextures26}. */
    record Registered(AbstractTexture texture, Identifier id) implements Texture26 {
    }
}
