package net.shattergui.platform.impl;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.shattergui.platform.UiTexture;

public interface Texture121 extends UiTexture {
    AbstractTexture texture();

    ResourceLocation id();

    record Registered(AbstractTexture texture, ResourceLocation id) implements Texture121 {
    }
}
