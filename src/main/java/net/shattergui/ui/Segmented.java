package net.shattergui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A choice of a few options as buttons side by side. Buttons are as wide as their labels; when they do not fit in one
 * line they flow onto the next, and each line is stretched to the full width. A label wider than the whole width is
 * the only one ever shortened, and then its tooltip shows it in full.
 */
public class Segmented extends Container {
    private static final int GAP = 3;
    private final List<Button> buttons = new ArrayList<>();

    /** Adds an option: drawn as chosen while {@code chosen} is true. */
    public Button option(Component label, Component description, BooleanSupplier chosen, Runnable action) {
        Button b = new Button(label, null, Button.Kind.GHOST, action) {
            @Override
            protected void draw(Ui ui) {
                selected(chosen.getAsBoolean());
                super.draw(ui);
            }
        };
        b.tooltip(label, description);
        buttons.add(b);
        return add(b);
    }

    @Override
    protected int measure(int width) {
        var font = Minecraft.getInstance().font;
        List<List<Button>> lines = new ArrayList<>();
        List<Button> line = new ArrayList<>();
        int used = 0;
        for (Button b : buttons) {
            int bw = Math.min(width, font.width(b.text()) + 14);
            if (!line.isEmpty() && used + GAP + bw > width) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            used += (line.isEmpty() ? 0 : GAP) + bw;
            line.add(b);
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        int cy = y;
        for (List<Button> l : lines) {
            int natural = 0;
            for (Button b : l) {
                natural += Math.min(width, font.width(b.text()) + 14);
            }
            int spare = Math.max(0, width - natural - GAP * (l.size() - 1));
            int cx = x;
            for (int i = 0; i < l.size(); i++) {
                Button b = l.get(i);
                int bw = Math.min(width, font.width(b.text()) + 14) + spare / l.size() + (i < spare % l.size() ? 1 : 0);
                b.setBounds(cx, cy, i == l.size() - 1 ? x + width - cx : bw, Theme.CONTROL_HEIGHT);
                cx += bw + GAP;
            }
            cy += Theme.CONTROL_HEIGHT + GAP;
        }
        return lines.isEmpty() ? 0 : cy - y - GAP;
    }
}
