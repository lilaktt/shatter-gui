package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A list of ids with a search field (vanilla particles, sounds): only the visible rows are drawn, the wheel scrolls,
 * a click chooses. The chosen id is highlighted and kept in sight when the list opens.
 */
final class SearchList extends Container {
    private static final int ROW = 11;
    private final Supplier<List<String>> all;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final int rows;
    private final TextField search = new TextField(48);
    private List<String> shown = List.of();
    private String lastQuery;
    private float scroll;
    private boolean revealed;

    SearchList(Supplier<List<String>> all, Supplier<String> getter, Consumer<String> setter, int rows) {
        this.all = all;
        this.getter = getter;
        this.setter = setter;
        this.rows = rows;
        search.icon(Icon.SEARCH).hint(EditorUi.tr("search"));
        add(search);
    }

    private void filter() {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery)) {
            return;
        }
        lastQuery = query;
        List<String> out = new ArrayList<>();
        for (String id : all.get()) {
            if (query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(id);
            }
        }
        shown = out;
        scroll = 0;
    }

    @Override
    protected int measure(int width) {
        search.layout(x, y, width);
        return search.h + 2 + rows * ROW + 4;
    }

    private int listY() {
        return search.y + search.h + 2;
    }

    private int listH() {
        return rows * ROW + 4;
    }

    private int maxScroll() {
        return Math.max(0, shown.size() * ROW - (listH() - 4));
    }

    @Override
    protected void draw(Ui ui) {
        filter();
        String chosen = getter.get();
        if (!revealed) {
            revealed = true;
            int index = shown.indexOf(chosen);
            if (index >= 0) {
                scroll = Math.clamp(index * ROW - (listH() - 4) / 2f, 0, maxScroll());
            }
        }
        super.draw(ui);
        int ly = listY();
        Draw.roundBox(ui, x, ly, w, listH(), Theme.RADIUS_CONTROL, Theme.FIELD, Theme.BORDER);
        ui.pushClip(x + 1, ly + 2, w - 2, listH() - 4);
        int first = Math.max(0, (int) (scroll / ROW));
        for (int i = first; i < Math.min(shown.size(), first + rows + 2); i++) {
            int ry = Math.round(ly + 2 + i * ROW - scroll);
            String id = shown.get(i);
            boolean on = id.equals(chosen);
            boolean over = ui.isHovered(this) && ui.mouseY >= ry && ui.mouseY < ry + ROW && ui.mouseY >= ly && ui.mouseX < x + w - 4;
            if (on || over) {
                Draw.rect(ui, x + 2, ry, w - 8, ROW, on ? Theme.accentAlpha(0x50) : 0x20FFFFFF);
            }
            String label = id.startsWith("minecraft:") ? id.substring(10) : id;
            ui.textFit(Component.literal(label), x + 5, ry + 2, w - 14, on ? Theme.TEXT : Theme.TEXT_DIM);
        }
        ui.popClip();
        if (shown.isEmpty()) {
            ui.textFit(EditorUi.tr("search.none"), x + 5, ly + 4, w - 10, Theme.TEXT_FAINT);
        }
        if (maxScroll() > 0) {
            int barH = Math.max(8, listH() * listH() / Math.max(1, shown.size() * ROW));
            int barY = ly + Math.round((listH() - barH) * (scroll / maxScroll()));
            Draw.rect(ui, x + w - 3, barY, 2, barH, Theme.BORDER_STRONG);
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0 || my < listY()) {
            return false;
        }
        int index = (int) Math.floor((my - listY() - 2 + scroll) / ROW);
        if (index >= 0 && index < shown.size()) {
            setter.accept(shown.get(index));
        }
        return true;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (maxScroll() <= 0) {
            return false;
        }
        scroll = (float) Math.clamp(scroll - amount * ROW * 2, 0, maxScroll());
        return true;
    }
}
