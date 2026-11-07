package net.shattergui.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.shattergui.platform.impl.ComponentText;
import net.shattergui.platform.impl.GuiDrawing26;
import net.shattergui.platform.impl.Input26;
import net.shattergui.platform.impl.Mc26;

/**
 * The screen class core's screens extend, on 26.1: a real {@link Screen} whose frame and input events are handed to
 * core in core's terms. Same members as the contract in core ({@code core/src/contract}).
 */
public abstract class PlatformScreen extends Screen {
    protected PlatformScreen(Text title) {
        super(ComponentText.unwrap(title));
    }

    // --- the game's events in core's terms ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderUi(new GuiDrawing26(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return uiMouseClicked(event.x(), event.y(), Mc26.uiMouseButton(event.button()), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return uiMouseReleased(event.x(), event.y(), Mc26.uiMouseButton(event.button()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return uiMouseDragged(event.x(), event.y(), Mc26.uiMouseButton(event.button()), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return uiKeyPressed(new Input26.Key(event));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return uiCharTyped(new Input26.Char(event));
    }

    /** One frame. */
    protected abstract void renderUi(GuiDrawing drawing, int mouseX, int mouseY, float partialTick);

    protected abstract boolean uiMouseClicked(double x, double y, int button, boolean doubleClick);

    protected abstract boolean uiMouseReleased(double x, double y, int button);

    protected abstract boolean uiMouseDragged(double x, double y, int button, double dx, double dy);

    protected abstract boolean uiKeyPressed(KeyInput key);

    protected abstract boolean uiCharTyped(CharInput character);

    // --- what core screens may ask the game ---

    /** The game's own handling of a key the UI did not take. */
    protected boolean platformKeyPressed(KeyInput key) {
        return super.keyPressed(((Input26.Key) key).event());
    }

    /** A world is loaded (false before the screen is opened). */
    protected boolean inWorld() {
        return minecraft != null && minecraft.level != null;
    }

    /** Runs on the game thread after the current event. */
    protected void runLater(Runnable task) {
        minecraft.execute(task);
    }

    /** Shows another screen (a core screen, or one the platform handed in, e.g. a parent; null: none). */
    protected void showScreen(Object screen) {
        Mc26.setScreen((Screen) screen);
    }

    /** The screen the game shows now (null before this one is opened). */
    protected Object currentScreen() {
        return minecraft == null ? null : Mc26.screen();
    }

    /** Asks the game's "open this link?" question, coming back here. */
    protected void confirmLink(String url) {
        Mc26.confirmLink(this, url);
    }
}
