package net.shattergui.config.screen;

import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.Widget;
import net.shattergui.ui.preview.PreviewPlayer;

/** An animated miniature of an effect: a looping {@link PreviewPlayer} in a framed box of fixed aspect. */
final class Thumb extends Widget {
    private final PreviewPlayer player;
    private final float aspect;
    private final int maxHeight;

    Thumb(PreviewPlayer player, float aspect, int maxHeight) {
        this.player = player;
        this.aspect = aspect;
        this.maxHeight = maxHeight;
    }

    @Override
    protected int measure(int width) {
        return Math.min(maxHeight, Math.max(16, Math.round(width * aspect)));
    }

    @Override
    public void layout(int x, int y, int w) {
        int height = measure(w);
        int width = Math.min(w, Math.round(height / aspect));
        // Centred in the space it was given.
        super.layout(x + (w - width) / 2, y, width);
        this.h = height;
    }

    @Override
    protected void draw(Ui ui) {
        Draw.roundRect(ui, x, y, w, h, 3, Theme.BORDER);
        ui.pushClip(x + 1, y + 1, w - 2, h - 2);
        player.render(ui, x + 1, y + 1, w - 2, h - 2);
        ui.popClip();
    }
}
