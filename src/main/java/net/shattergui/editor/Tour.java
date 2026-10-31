package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.ui.Anim;
import net.shattergui.ui.Button;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.util.List;
import java.util.function.Supplier;

/**
 * The short tour on first opening: four steps, each dimming everything but one part of the editor (layers, preview,
 * timeline, inspector) with a few words on it. "Next", "Back", "Skip"; Esc skips. Seen once, then remembered.
 */
final class Tour extends UiScreen.Overlay {
    /** One step: the part it points at and its text key. */
    record Step(Supplier<Widget> target, String key) {
    }

    private final List<Step> steps;
    private final java.util.function.BooleanSupplier alive;
    private final Runnable done;
    private final Button back;
    private final Button next;
    private final Button skip;
    private final Anim move = new Anim(0f, 10f);
    private final float[] rect = new float[4];
    private int step;
    private boolean placed;

    /** @param alive false once the editor is gone (another tab): the tour then closes itself */
    Tour(List<Step> steps, java.util.function.BooleanSupplier alive, Runnable done) {
        this.steps = steps;
        this.alive = alive;
        this.done = done;
        back = add(new Button(EditorUi.tr("tour.back"), null, Button.Kind.GHOST, () -> go(step - 1)));
        next = add(new Button(EditorUi.tr("tour.next"), null, Button.Kind.PRIMARY, () -> go(step + 1)));
        skip = add(new Button(EditorUi.tr("tour.skip"), null, Button.Kind.GHOST, this::finish));
    }

    int step() {
        return step;
    }

    void go(int index) {
        if (index >= steps.size()) {
            finish();
            return;
        }
        step = Math.max(0, index);
        move.snap(0f);
    }

    private void finish() {
        UiScreen.active().closeOverlay(this);
    }

    @Override
    protected void onClosed() {
        done.run();
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public void outsideClick() {
        // Only the buttons move the tour on.
    }

    @Override
    public void escape() {
        finish();
    }

    @Override
    public void place(int screenWidth, int screenHeight) {
        x = 0;
        y = 0;
        w = screenWidth;
        h = screenHeight;
        Widget target = steps.get(step).target().get();
        float[] want = {target.x - 3, target.y - 3, target.w + 6, target.h + 6};
        float t = move.target(1f).tick();
        for (int i = 0; i < 4; i++) {
            rect[i] = placed ? rect[i] + (want[i] - rect[i]) * Math.min(1f, t) : want[i];
        }
        placed = true;
        // The callout: beside the target where there is room.
        int cw = Math.min(200, screenWidth - 16);
        int ch = calloutHeight(cw);
        int cx;
        int cy;
        if (want[0] + want[2] + cw + 10 < screenWidth) {
            cx = Math.round(want[0] + want[2] + 8);
            cy = Math.round(Math.clamp(want[1], 8, screenHeight - ch - 8));
        } else if (want[0] - cw - 10 > 0) {
            cx = Math.round(want[0] - cw - 8);
            cy = Math.round(Math.clamp(want[1], 8, screenHeight - ch - 8));
        } else {
            cx = (screenWidth - cw) / 2;
            cy = Math.round(want[1] + want[3] + 8 + ch < screenHeight ? want[1] + want[3] + 8 : Math.max(8, want[1] - ch - 8));
        }
        calloutX = cx;
        calloutY = cy;
        calloutW = cw;
        calloutH = ch;
        int by = cy + ch - 22;
        int bw = Math.max(50, next.naturalWidth());
        next.setBounds(cx + cw - 8 - bw, by, bw, 16);
        int backW = Math.max(40, back.naturalWidth());
        back.setBounds(next.x - 4 - backW, by, backW, 16);
        back.visible = step > 0;
        next.text(EditorUi.tr(step == steps.size() - 1 ? "tour.done" : "tour.next"));
        skip.setBounds(cx + 6, by, Math.max(40, skip.naturalWidth()), 16);
        skip.visible = step < steps.size() - 1;
    }

    private int calloutX;
    private int calloutY;
    private int calloutW;
    private int calloutH;

    private int calloutHeight(int cw) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        Component text = EditorUi.tr("tour." + steps.get(step).key() + ".text");
        return 8 + Theme.LINE + 4 + font.split(text, cw - 16).size() * Theme.LINE + 8 + 22;
    }

    @Override
    protected void draw(Ui ui) {
        if (!alive.getAsBoolean()) {
            // Drawn from a copy of the overlay list, so closing here is safe.
            finish();
            return;
        }
        // Dim around the target (four rectangles), outline it.
        int rx = Math.round(rect[0]);
        int ry = Math.round(rect[1]);
        int rw = Math.round(rect[2]);
        int rh = Math.round(rect[3]);
        int dim = 0xA0000000;
        Draw.rect(ui, 0, 0, w, Math.max(0, ry), dim);
        Draw.rect(ui, 0, ry + rh, w, Math.max(0, h - ry - rh), dim);
        Draw.rect(ui, 0, ry, Math.max(0, rx), rh, dim);
        Draw.rect(ui, rx + rw, ry, Math.max(0, w - rx - rw), rh, dim);
        Draw.roundOutline(ui, rx, ry, rw, rh, 5, Theme.accent());
        Draw.shadow(ui, calloutX, calloutY, calloutW, calloutH, Theme.RADIUS_CARD, 1f);
        Draw.roundBox(ui, calloutX, calloutY, calloutW, calloutH, Theme.RADIUS_CARD, 0xFF17181F, Theme.accentAlpha(0xC0));
        String key = steps.get(step).key();
        ui.text(EditorUi.tr("tour." + key + ".title"), calloutX + 8, calloutY + 8, Theme.TEXT);
        ui.textSmall((step + 1) + "/" + steps.size(), calloutX + calloutW - 22, calloutY + 9, 18, Theme.TEXT_FAINT);
        ui.textWrapped(EditorUi.tr("tour." + key + ".text"), calloutX + 8, calloutY + 8 + Theme.LINE + 4, calloutW - 16, Theme.TEXT_DIM);
        super.draw(ui);
    }

    @Override
    public boolean key(net.minecraft.client.input.KeyEvent event) {
        if (event.isConfirmation() || event.isRight()) {
            go(step + 1);
            return true;
        }
        if (event.isLeft()) {
            go(step - 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean focusable() {
        return true;
    }
}
