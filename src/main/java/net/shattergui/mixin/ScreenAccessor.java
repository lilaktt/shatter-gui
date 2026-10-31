package net.shattergui.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The protected {@code Screen#init()}: lays out a screen without {@code Screen#init(int, int)}, the entry point Fabric's
 * screen events (and through them other mods, such as Spatial GUI) hook. Used for the editor's real preview only.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("init")
    void shatterGui$init();
}
