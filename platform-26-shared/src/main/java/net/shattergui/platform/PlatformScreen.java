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

public abstract class PlatformScreen extends Screen {
    protected PlatformScreen(Text title) {
        super(ComponentText.unwrap(title));
    }

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

    protected abstract void renderUi(GuiDrawing drawing, int mouseX, int mouseY, float partialTick);

    protected abstract boolean uiMouseClicked(double x, double y, int button, boolean doubleClick);

    protected abstract boolean uiMouseReleased(double x, double y, int button);

    protected abstract boolean uiMouseDragged(double x, double y, int button, double dx, double dy);

    protected abstract boolean uiKeyPressed(KeyInput key);

    protected abstract boolean uiCharTyped(CharInput character);

    protected boolean platformKeyPressed(KeyInput key) {
        return super.keyPressed(((Input26.Key) key).event());
    }

    protected boolean inWorld() {
        return minecraft != null && minecraft.level != null;
    }

    protected void runLater(Runnable task) {
        minecraft.execute(task);
    }

    protected void showScreen(Object screen) {
        Mc26.setScreen((Screen) screen);
    }

    protected Object currentScreen() {
        return minecraft == null ? null : Mc26.screen();
    }

    protected void confirmLink(String url) {
        Mc26.confirmLink(this, url);
    }
}
