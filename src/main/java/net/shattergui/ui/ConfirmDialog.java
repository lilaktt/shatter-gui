package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A modal question with two answers. Opens with a short fade and scale; Enter confirms, Esc cancels, Tab moves
 * between the two buttons.
 */
public class ConfirmDialog extends UiScreen.Overlay {
    private final Component title;
    private final Component message;
    private final Button confirm;
    private final Button cancel;
    private final Anim appear = new Anim(0f, Theme.SPEED_APPEAR + 3f);
    private int messageH;

    public ConfirmDialog(Component title, Component message, Component confirmText, boolean danger, Runnable onConfirm) {
        this.title = title;
        this.message = message;
        this.confirm = add(new Button(confirmText, null, danger ? Button.Kind.DANGER : Button.Kind.PRIMARY, () -> {
            UiScreen.active().closeOverlay(this);
            onConfirm.run();
        }));
        this.cancel = add(new Button(Component.translatable("gui.cancel"), null, Button.Kind.SECONDARY,
                () -> UiScreen.active().closeOverlay(this)));
    }

    public static void open(Component title, Component message, Component confirmText, boolean danger, Runnable onConfirm) {
        ConfirmDialog dialog = new ConfirmDialog(title, message, confirmText, danger, onConfirm);
        UiScreen.active().openOverlay(dialog);
        UiScreen.active().setFocus(dialog.confirm);
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public void outsideClick() {
        // Modal: a click outside does nothing.
    }

    @Override
    public void place(int screenWidth, int screenHeight) {
        w = Math.min(260, screenWidth - 24);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        messageH = font.split(message, w - 24).size() * Theme.LINE;
        h = 14 + Theme.LINE + 6 + messageH + 12 + Theme.CONTROL_HEIGHT + 12;
        x = (screenWidth - w) / 2;
        y = (screenHeight - h) / 2;
        int bw = Math.max(70, Math.max(confirm.naturalWidth(), cancel.naturalWidth()));
        cancel.setBounds(x + w - 12 - bw, y + h - 12 - Theme.CONTROL_HEIGHT, bw, Theme.CONTROL_HEIGHT);
        confirm.setBounds(cancel.x - 6 - bw, cancel.y, bw, Theme.CONTROL_HEIGHT);
    }

    @Override
    protected void draw(Ui ui) {
        float a = appear.target(1f).tick();
        float scale = 0.94f + 0.06f * a;
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(x + w / 2f, y + h / 2f);
        ui.g.pose().scale(scale, scale);
        ui.g.pose().translate(-(x + w / 2f), -(y + h / 2f));
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CARD, 1f);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, Theme.fade(0xFF17181F, Math.max(0.5f, a)), Theme.BORDER_STRONG);
        Draw.roundRect(ui, x, y, w, 3, 1, Theme.accent());
        ui.text(title, x + 12, y + 14, Theme.TEXT);
        ui.textWrapped(message, x + 12, y + 14 + Theme.LINE + 6, w - 24, Theme.TEXT_DIM);
        confirm.render(ui);
        cancel.render(ui);
        ui.g.pose().popMatrix();
    }

    @Override
    public boolean key(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            confirm.press();
            return true;
        }
        return false;
    }

    @Override
    public boolean focusable() {
        return false;
    }
}
