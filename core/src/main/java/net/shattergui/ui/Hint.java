package net.shattergui.ui;

import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;
import net.shattergui.platform.TextLine;

import java.util.List;
import java.util.function.Supplier;

public class Hint extends Widget {
    private final Supplier<Text> text;
    private List<TextLine> lines = List.of();

    public Hint(Text text) {
        this(() -> text);
    }

    public Hint(Supplier<Text> text) {
        this.text = text;
    }

    private int lineH() {
        return Math.round(Theme.LINE * Ui.smallScale());
    }

    @Override
    protected int measure(int width) {
        int inner = Math.max(10, Math.round((width - 10) / Ui.smallScale()));
        lines = Platform.get().texts().font().split(text.get(), inner);
        return Math.max(1, lines.size()) * lineH() + 6;
    }

    @Override
    protected void draw(Ui ui) {
        Draw.roundRect(ui, x, y, w, h, 3, 0x12FFFFFF);
        Draw.rect(ui, x, y + 2, 2, h - 4, Theme.accentAlpha(0x90));
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(x + 6, y + 3);
        float scale = Ui.smallScale();
        ui.g.pose().scale(scale, scale);
        int ly = 0;
        for (TextLine line : lines) {
            ui.g.text(ui.font, line, 0, ly, Theme.TEXT_DIM, false);
            ly += Theme.LINE;
        }
        ui.g.pose().popMatrix();
    }

    @Override
    public Widget hit(double mx, double my) {
        return null;
    }
}
