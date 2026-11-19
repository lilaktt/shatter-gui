package net.shattergui.platform;

public abstract class PlatformScreen {
    public int width;
    public int height;

    protected PlatformScreen(Text title) {
    }

    protected void init() {
    }

    public void removed() {
    }

    public void onClose() {
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return false;
    }

    protected abstract void renderUi(GuiDrawing drawing, int mouseX, int mouseY, float partialTick);

    protected abstract boolean uiMouseClicked(double x, double y, int button, boolean doubleClick);

    protected abstract boolean uiMouseReleased(double x, double y, int button);

    protected abstract boolean uiMouseDragged(double x, double y, int button, double dx, double dy);

    protected abstract boolean uiKeyPressed(KeyInput key);

    protected abstract boolean uiCharTyped(CharInput character);

    protected boolean platformKeyPressed(KeyInput key) {
        return false;
    }

    protected boolean inWorld() {
        return false;
    }

    protected void runLater(Runnable task) {
    }

    protected void showScreen(Object screen) {
    }

    protected Object currentScreen() {
        return null;
    }

    protected void confirmLink(String url) {
    }
}
