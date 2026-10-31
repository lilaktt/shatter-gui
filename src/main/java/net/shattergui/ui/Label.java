package net.shattergui.ui;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Text, wrapped to the width. A heading style is a little brighter with extra space above. */
public class Label extends Widget {
    public enum Style {
        HEADING, BODY, DIM, SMALL
    }

    private Supplier<Component> text;
    private final Style style;

    public Label(Component text, Style style) {
        this(() -> text, style);
    }

    public Label(Supplier<Component> text, Style style) {
        this.text = text;
        this.style = style;
    }

    public Label text(Component value) {
        this.text = () -> value;
        return this;
    }

    @Override
    protected int measure(int width) {
        if (style == Style.SMALL) {
            float scale = Ui.smallScale();
            int lines = net.minecraft.client.Minecraft.getInstance().font.split(text.get(), Math.max(10, Math.round(width / scale))).size();
            return Math.max(1, lines) * Math.round(Theme.LINE * scale) - 1;
        }
        int lines = net.minecraft.client.Minecraft.getInstance().font.split(text.get(), Math.max(10, width)).size();
        return Math.max(1, lines) * Theme.LINE - 1 + (style == Style.HEADING ? 3 : 0);
    }

    @Override
    protected void draw(Ui ui) {
        switch (style) {
            case HEADING -> {
                ui.textWrapped(text.get(), x, y + 3, w, Theme.TEXT);
            }
            case BODY -> ui.textWrapped(text.get(), x, y, w, Theme.TEXT);
            case DIM -> ui.textWrapped(text.get(), x, y, w, Theme.TEXT_DIM);
            case SMALL -> {
                // Wrapped at the small size, never cut off.
                float scale = Ui.smallScale();
                ui.g.pose().pushMatrix();
                ui.g.pose().translate(x, y);
                ui.g.pose().scale(scale, scale);
                ui.textWrapped(text.get(), 0, 0, Math.max(10, Math.round(w / scale)), Theme.TEXT_FAINT);
                ui.g.pose().popMatrix();
            }
        }
    }
}
