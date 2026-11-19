package net.shattergui.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.platform.impl.ComponentText;
import net.shattergui.platform.impl.GuiDrawing121;
import net.shattergui.platform.impl.Input121;

public abstract class PlatformScreen extends Screen {
    protected PlatformScreen(Text title) {
        super(ComponentText.unwrap(title));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderUi(new GuiDrawing121(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
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
        return uiCharTyped(new Input121.Char(character));
    }

    protected abstract void renderUi(GuiDrawing drawing, int mouseX, int mouseY, float partialTick);

    protected abstract boolean uiMouseClicked(double x, double y, int button, boolean doubleClick);

    protected abstract boolean uiMouseReleased(double x, double y, int button);

    protected abstract boolean uiMouseDragged(double x, double y, int button, double dx, double dy);

    protected abstract boolean uiKeyPressed(KeyInput key);

    protected abstract boolean uiCharTyped(CharInput character);

    protected boolean platformKeyPressed(KeyInput key) {
        Input121.Key k = (Input121.Key) key;
        return super.keyPressed(k.key(), k.scancode(), k.modifiers());
    }

    protected boolean inWorld() {
        return minecraft != null && minecraft.level != null;
    }

    protected void runLater(Runnable task) {
        minecraft.execute(task);
    }

    protected void showScreen(Object screen) {
        minecraft.setScreen((Screen) screen);
    }

    protected Object currentScreen() {
        return minecraft == null ? null : minecraft.screen;
    }

    protected void confirmLink(String url) {
        ConfirmLinkScreen.confirmLinkNow(this, url);
    }
}
