package net.shattergui.platform.impl;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.shattergui.platform.UiTexture;

/** A UI texture on 1.21.1: the game's texture and the id it is registered under. */
public interface Texture121 extends UiTexture {
    AbstractTexture texture();

    ResourceLocation id();

    /** A texture registered by the platform's {@code UiTextures}. */
    record Registered(AbstractTexture texture, ResourceLocation id) implements Texture121 {
    }
}
