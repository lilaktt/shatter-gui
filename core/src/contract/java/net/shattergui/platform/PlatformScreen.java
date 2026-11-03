package net.shattergui.platform;

/**
 * The contract of the game's screen class that core's screens extend. Every platform module has a class with this name
 * and these members (accessible the same way), built on the game's own screen, so a core screen is a real game screen
 * there. This copy is compiled with core only, to check that core uses nothing else; it is never packaged.
 */
public abstract class PlatformScreen {
    /** Size in GUI pixels, set by the game. */
    public int width;
    public int height;

    protected PlatformScreen(Text title) {
    }

    // --- what the game calls (core screens override these) ---

    /** The screen was opened or resized. */
    protected void init() {
    }

    /** The screen is no longer shown. */
    public void removed() {
    }

    /** The screen asks to close (Esc, a close button). */
    public void onClose() {
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return false;
    }

    // --- the game's events in core's terms (the platform's screen forwards its own events here) ---

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
        return false;
    }

    /** A world is loaded (false before the screen is opened). */
    protected boolean inWorld() {
        return false;
    }

    /** Runs on the game thread after the current event. */
    protected void runLater(Runnable task) {
    }

    /** Shows another screen (a core screen, or one the platform handed in, e.g. a parent; null: none). */
    protected void showScreen(Object screen) {
    }

    /** The screen the game shows now (null before this one is opened). */
    protected Object currentScreen() {
        return null;
    }

    /** Asks the game's "open this link?" question, coming back here. */
    protected void confirmLink(String url) {
    }
}
