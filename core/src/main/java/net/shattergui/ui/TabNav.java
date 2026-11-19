package net.shattergui.ui;

import net.shattergui.platform.KeyInput;
import net.shattergui.platform.Keys;
import net.shattergui.platform.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class TabNav extends Widget {
    public record Tab(Icon icon, Text label, Text description) {
    }

    private final List<Tab> tabs = new ArrayList<>();
    private final boolean vertical;
    private final IntConsumer onSelect;
    private int selected;
    private int keyboard = -1;
    private final Anim indicator = new Anim(0f, Theme.SPEED_INDICATOR);
    private final List<Anim> hovers = new ArrayList<>();
    private boolean compact;
    private int itemSize = 22;

    public TabNav(boolean vertical, IntConsumer onSelect) {
        this.vertical = vertical;
        this.onSelect = onSelect;
    }

    public TabNav add(Icon icon, Text label, Text description) {
        tabs.add(new Tab(icon, label, description));
        hovers.add(new Anim(0f, Theme.SPEED_HOVER));
        return this;
    }

    public void select(int index, boolean animate) {
        selected = index;
        if (!animate) {
            indicator.snap(index);
        }
    }

    public int tabCount() {
        return tabs.size();
    }

    public void activate(int index) {
        if (index >= 0 && index < tabs.size()) {
            choose(index);
        }
    }

    public int selected() {
        return selected;
    }

    public int[] center(int i) {
        return new int[]{itemX(i) + itemW() / 2, itemY(i) + itemSize / 2};
    }

    public void compact(boolean value) {
        compact = value;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected int measure(int width) {
        return vertical ? tabs.size() * itemSize : itemSize;
    }

    private int itemX(int i) {
        return vertical ? x : x + i * (w / Math.max(1, tabs.size()));
    }

    private int itemY(int i) {
        return vertical ? y + i * itemSize : y;
    }

    private int itemW() {
        return vertical ? w : w / Math.max(1, tabs.size());
    }

    private int indexAt(double mx, double my) {
        for (int i = 0; i < tabs.size(); i++) {
            if (mx >= itemX(i) && mx < itemX(i) + itemW() && my >= itemY(i) && my < itemY(i) + itemSize) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void draw(Ui ui) {
        float pos = indicator.target(selected).tick();
        int iw = itemW();
        if (vertical) {
            int iy = Math.round(y + pos * itemSize);
            Draw.roundRect(ui, x, iy, iw, itemSize, Theme.RADIUS_CONTROL, Theme.accentAlpha(0x30));
            Draw.roundRect(ui, x, iy + 4, 2, itemSize - 8, 1, Theme.accent());
        } else {
            int ix = Math.round(x + pos * iw);
            Draw.roundRect(ui, ix + 4, y + itemSize - 2, iw - 8, 2, 1, Theme.accent());
        }
        int over = ui.isHovered(this) ? indexAt(ui.mouseX, ui.mouseY) : -1;
        Tab tip = null;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            float hv = hovers.get(i).target(i == over).tick();
            int ix = itemX(i);
            int iy = itemY(i);
            if (i != selected && hv > 0.01f) {
                Draw.roundRect(ui, ix, iy, iw, itemSize, Theme.RADIUS_CONTROL, Theme.fade(Theme.RAISED, hv * 0.8f));
            }
            if (ui.isFocused(this) && (keyboard == i || keyboard < 0 && i == selected)) {
                Draw.roundOutline(ui, ix, iy, iw, itemSize, Theme.RADIUS_CONTROL, Theme.accentAlpha(0xC0));
            }
            boolean on = i == selected;
            int color = on ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv);
            int iconColor = on ? Theme.accentHover() : color;
            if (compact || tab.label() == null) {
                Draw.iconCentered(ui, tab.icon(), ix, iy, iw, itemSize, iconColor);
            } else {
                int contentX = vertical ? ix + 9 : ix + (iw - Icon.SIZE - 5 - ui.font.width(tab.label())) / 2;
                Draw.icon(ui, tab.icon(), contentX, iy + (itemSize - Icon.SIZE) / 2, 1, iconColor);
                ui.textFit(tab.label(), contentX + Icon.SIZE + 6, iy + (itemSize - 8) / 2, ix + iw - (contentX + Icon.SIZE + 8), color);
            }
            if (i == over) {
                tip = tab;
            }
        }
        if (tip == null && !tabs.isEmpty()) {
            tip = tabs.get(Math.clamp(keyboard >= 0 ? keyboard : selected, 0, tabs.size() - 1));
        }
        if (tip != null) {
            tooltip(tip.label(), tip.description());
        } else {
            tooltip(null, null);
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        int i = indexAt(mx, my);
        if (button == 0 && i >= 0) {
            choose(i);
            return true;
        }
        return false;
    }

    private void choose(int i) {
        if (i != selected) {
            click();
        }
        selected = i;
        keyboard = -1;
        onSelect.accept(i);
    }

    @Override
    public boolean key(KeyInput event) {
        int key = event.key();
        int current = keyboard >= 0 ? keyboard : selected;
        boolean next = vertical ? key == Keys.KEY_DOWN : key == Keys.KEY_RIGHT;
        boolean prev = vertical ? key == Keys.KEY_UP : key == Keys.KEY_LEFT;
        if (next || prev) {
            keyboard = Math.floorMod(current + (next ? 1 : -1), tabs.size());
            choose(keyboard);
            return true;
        }
        if (key == Keys.KEY_RETURN || key == Keys.KEY_SPACE || key == Keys.KEY_NUMPADENTER) {
            choose(current);
            return true;
        }
        return false;
    }

    @Override
    public void focusChanged(boolean focused) {
        keyboard = -1;
    }
}
