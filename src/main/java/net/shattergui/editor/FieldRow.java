package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxProblems;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.Widget;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One field of the inspector: its human name on top (with a help tooltip and, when the value is not the default, a
 * "back to default" button), the control below, and under it what the checks say about this field — errors in red,
 * warnings in amber, each with the reason. Problems are found by the field's path ({@link FxEditor#problemsAt}).
 */
final class FieldRow extends Container {
    private static final int LABEL_H = 11;
    private final FxEditor editor;
    private final Component label;
    private final String path;
    private final boolean exact;
    private final Widget control;
    private final Button reset;
    private final BooleanSupplier changed;
    private List<FxProblems.Problem> shown = List.of();
    private List<net.minecraft.util.FormattedCharSequence> labelLines = List.of();

    /**
     * @param path    the field's path in the effect (problems under it are shown), or null for none
     * @param reset   puts the default back (null: no reset button)
     * @param changed true when the value differs from the default (shows the reset button)
     */
    FieldRow(FxEditor editor, Component label, Component help, String path, Widget control, Runnable reset,
             BooleanSupplier changed) {
        this(editor, label, help, path, false, control, reset, changed);
    }

    FieldRow(FxEditor editor, Component label, Component help, String path, boolean exact, Widget control, Runnable reset,
             BooleanSupplier changed) {
        this.editor = editor;
        this.label = label;
        this.path = path;
        this.exact = exact;
        this.control = add(control);
        this.changed = changed == null ? () -> false : changed;
        this.reset = reset == null ? null : add(Button.icon(Icon.RESET, EditorUi.tr("reset"), () -> {
            reset.run();
            editor.refreshInspector();
        }));
        tooltip(label, help);
    }

    @Override
    protected int measure(int width) {
        shown = path == null ? List.of() : editor.problemsAt(path, exact);
        var labelFont = net.minecraft.client.Minecraft.getInstance().font;
        // The name wraps (never cut off); the reset button sits at the end of its first line.
        labelLines = labelFont.split(label, Math.max(20, width - (reset != null ? 14 : 0)));
        int cy = y + Math.max(1, labelLines.size()) * Theme.LINE + 1;
        control.layout(x, cy, width);
        cy += control.h;
        if (reset != null) {
            reset.visible = changed.getAsBoolean();
            reset.setBounds(x + width - 12, y - 1, 12, 11);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < Math.min(3, shown.size()); i++) {
            cy += 2 + font.split(message(shown.get(i)), Math.max(20, width - 12)).size() * Theme.LINE;
        }
        return cy - y;
    }

    private static Component message(FxProblems.Problem p) {
        return Component.literal(p.message());
    }

    @Override
    protected void draw(Ui ui) {
        boolean error = shown.stream().anyMatch(FxProblems.Problem::error);
        int labelColor = error ? Theme.mix(Theme.TEXT_DIM, Theme.DANGER, 0.7f) : Theme.TEXT_DIM;
        int ly = y + 1;
        for (var line : labelLines) {
            ui.g.text(ui.font, line, x, ly, labelColor, false);
            ly += Theme.LINE;
        }
        if (error) {
            Draw.roundOutline(ui, control.x - 1, control.y - 1, control.w + 2, control.h + 2, Theme.RADIUS_CONTROL + 1,
                    Theme.fade(Theme.DANGER, 0.8f));
        }
        super.draw(ui);
        int cy = control.y + control.h;
        for (int i = 0; i < Math.min(3, shown.size()); i++) {
            FxProblems.Problem p = shown.get(i);
            int color = p.error() ? 0xFFFCA5A5 : 0xFFFCD34D;
            cy += 2;
            Draw.icon(ui, Icon.WARNING, x, cy, 1, p.error() ? Theme.DANGER : Theme.WARNING);
            cy += ui.textWrapped(message(p), x + 12, cy + 1, w - 12, color);
        }
    }
}
