package net.shattergui.editor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

/**
 * The effect editor on the whole screen. It shows the same editor as the settings' "Custom" tab (nothing is lost
 * going back and forth); Esc or the full-screen button returns to the settings. Like every editor screen of the mod
 * it never gets an effect itself.
 */
public final class FxEditorScreen extends UiScreen {
    private final Screen parent;
    private final FxEditor editor;
    private final Runnable onReturn;

    /**
     * @param parent   where Esc goes back to (the settings)
     * @param onReturn called before going back (the settings take the editor back)
     */
    public FxEditorScreen(Screen parent, FxEditor editor, Runnable onReturn) {
        super(net.minecraft.network.chat.Component.translatable("ui.shatter_gui.fx.screen_title"));
        this.parent = parent;
        this.editor = editor;
        this.onReturn = onReturn;
    }

    public FxEditor editor() {
        return editor;
    }

    @Override
    protected void init() {
        super.init();
        root.clear();
        root.add(new Backdrop());
        root.add(editor);
    }

    @Override
    protected void layoutRoot(int width, int height) {
        int margin = width < 520 ? 4 : 8;
        editor.visible = true;
        editor.setBounds(margin + 4, margin + 4, width - margin * 2 - 8, height - margin * 2 - 8);
    }

    @Override
    protected boolean shortcut(KeyEvent event) {
        return editor.shortcut(event);
    }

    @Override
    protected void escape() {
        back();
    }

    @Override
    public void onClose() {
        back();
    }

    public void back() {
        onReturn.run();
        minecraft.setScreen(parent);
    }

    private final class Backdrop extends Widget {
        @Override
        protected void draw(Ui ui) {
            ui.g.fillGradient(0, 0, width, height, Theme.BACKDROP_TOP, Theme.BACKDROP_BOTTOM);
            int margin = width < 520 ? 4 : 8;
            Draw.shadow(ui, margin, margin, width - margin * 2, height - margin * 2, 8, 1f);
            Draw.roundBox(ui, margin, margin, width - margin * 2, height - margin * 2, 8, Theme.PANEL, Theme.BORDER);
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }
}
