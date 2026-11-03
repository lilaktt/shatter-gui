package net.shattergui.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.platform.impl.ComponentText;
import net.shattergui.platform.impl.GuiDrawing121;
import net.shattergui.platform.impl.Input121;

/**
 * The screen class core's screens extend, on 1.21.1: a real {@link Screen}. 1.21.1 draws the GUI at once (no render
 * state) and reports input as plain numbers, so the events are turned into core's terms here.
 */
public abstract class PlatformScreen extends Screen {
    protected PlatformScreen(Text title) {
        super(ComponentText.unwrap(title));
    }

    // --- the game's events in core's terms ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderUi(new GuiDrawing121(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        // Not ported: 1.21.1 does not report double clicks (26.1's MouseHandler does); they need a timer here.
        return uiMouseClicked(x, y, button, false);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return uiMouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return uiMouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return uiKeyPressed(new Input121.Key(key, scancode, modifiers));
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        // 1.21.1 reports UTF-16 units: a character outside the BMP arrives as two calls (not ported yet).
        return uiCharTyped(new Input121.Char(character));
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
        Input121.Key k = (Input121.Key) key;
        return super.keyPressed(k.key(), k.scancode(), k.modifiers());
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
        minecraft.setScreen((Screen) screen);
    }

    /** The screen the game shows now (null before this one is opened). */
    protected Object currentScreen() {
        return minecraft == null ? null : minecraft.screen;
    }

    /** Asks the game's "open this link?" question, coming back here. */
    protected void confirmLink(String url) {
        ConfirmLinkScreen.confirmLinkNow(this, url);
    }
}
