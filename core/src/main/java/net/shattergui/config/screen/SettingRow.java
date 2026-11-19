package net.shattergui.config.screen;

import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.Widget;

import java.util.function.BooleanSupplier;

final class SettingRow extends Container {
    private final Name name;
    private final Widget control;
    private final Button reset;
    private final BooleanSupplier changed;

    SettingRow(Text title, Text help, boolean showHelp, Widget control, BooleanSupplier changed, Runnable onReset) {
        this.name = add(new Name(title, help, showHelp));
        this.control = add(control);
        tooltip(title, help);
        this.changed = changed;
        if (onReset != null) {
            this.reset = add(Button.icon(Icon.RESET, Text.translatable("ui.shatter_gui.reset"), onReset));
            reset.tooltip(Text.translatable("ui.shatter_gui.reset"), Text.translatable("ui.shatter_gui.reset.tooltip"));
        } else {
            this.reset = null;
        }
    }

    private boolean narrow(int width) {
        return width < 280;
    }

    @Override
    protected int measure(int width) {
        int resetW = reset != null ? Theme.CONTROL_HEIGHT + 4 : 0;
        if (narrow(width)) {
            name.layout(x, y, width - resetW);
            control.layout(x, y + name.h + 3, width - resetW);
            if (reset != null) {
                reset.setBounds(x + width - Theme.CONTROL_HEIGHT, control.y + (control.h - Theme.CONTROL_HEIGHT) / 2,
                        Theme.CONTROL_HEIGHT, Theme.CONTROL_HEIGHT);
            }
            return name.h + 3 + control.h;
        }
        int controlW = Math.clamp(Math.round(width * 0.46f), 110, 200);
        name.layout(x, y, width - controlW - resetW - 8);
        int controlX = x + width - resetW - controlW;
        control.layout(controlX, y, controlW);
        int height = Math.max(Math.max(name.h, Theme.CONTROL_HEIGHT), control.h);
        control.layout(controlX, y + (height - control.h) / 2, controlW);
        name.layout(x, y + Math.max(0, (height - name.h) / 2), width - controlW - resetW - 8);
        if (reset != null) {
            reset.setBounds(x + width - Theme.CONTROL_HEIGHT, y + (height - Theme.CONTROL_HEIGHT) / 2,
                    Theme.CONTROL_HEIGHT, Theme.CONTROL_HEIGHT);
        }
        return height;
    }

    @Override
    protected void draw(Ui ui) {
        if (reset != null) {
            reset.enabled = changed.getAsBoolean();
        }
        super.draw(ui);
    }

    private static final class Name extends Widget {
        private final Text title;
        private final Text help;
        private final boolean showHelp;

        Name(Text title, Text help, boolean showHelp) {
            this.title = title;
            this.help = help;
            this.showHelp = showHelp;
            tooltip(title, help);
        }

        @Override
        protected int measure(int width) {
            var font = Platform.get().texts().font();
            int lines = font.split(title, Math.max(10, width - 12)).size();
            int h = lines * Theme.LINE - 1;
            if (showHelp && help != null) {
                h += 2 + font.split(help, Math.max(10, width)).size() * Theme.LINE;
            }
            return Math.max(h, 9);
        }

        @Override
        protected void draw(Ui ui) {
            int titleH = ui.textWrapped(title, x, y, w - 12, Theme.mix(Theme.TEXT, 0xFFFFFFFF, hover.value() * 0.3f));
            if (help != null) {
                int lastLineW = Math.min(w - 12, ui.font.width(title));
                int ix = x + Math.min(lastLineW, w - 12) + 4;
                int iy = y + titleH - Theme.LINE - 1;
                if (ui.font.split(title, Math.max(10, w - 12)).size() > 1) {
                    ix = x + w - 10;
                }
                ui.g.pose().pushMatrix();
                ui.g.pose().translate(ix, iy + 1);
                ui.g.pose().scale(0.8f, 0.8f);
                net.shattergui.ui.Draw.icon(ui, Icon.INFO, 0, 0, 1, Theme.mix(Theme.TEXT_FAINT, Theme.accentHover(), hover.value()));
                ui.g.pose().popMatrix();
            }
            if (showHelp && help != null) {
                ui.textWrapped(help, x, y + titleH + 2, w, Theme.TEXT_DIM);
            }
        }
    }
}
