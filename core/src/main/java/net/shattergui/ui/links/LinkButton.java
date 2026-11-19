package net.shattergui.ui.links;

import net.shattergui.links.LinkStatus;
import net.shattergui.platform.Text;
import net.shattergui.ui.Button;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.function.BooleanSupplier;

public final class LinkButton extends Button {
    public enum Brand {
        MODRINTH(0xFF1BD96A, "modrinth", LinkStatus.Page.MODRINTH),
        CURSEFORGE(0xFFF16436, "curseforge", null),
        GITHUB(0xFFE6EDF3, "github", LinkStatus.Page.GITHUB),
        BUG(0xFFE6EDF3, "bug", LinkStatus.Page.GITHUB);

        public final int color;
        public final String file;
        public final LinkStatus.Page page;

        Brand(int color, String file, LinkStatus.Page page) {
            this.color = color;
            this.file = file;
            this.page = page;
        }
    }

    public static final int GREEN = 0xFF22C55E;
    public static final int YELLOW = 0xFFEAB308;
    public static final int GREY = 0xFF8B8F9C;

    private final Brand brand;
    private final Text title;
    private final Text tooltip;
    private final BooleanSupplier checkEnabled;
    private final String version;
    private int iconX = -1;
    private int iconY = -1;

    public LinkButton(Brand brand, Text title, Text tooltip, BooleanSupplier checkEnabled, String version) {
        super(title, null, Kind.SECONDARY, null);
        this.brand = brand;
        this.title = title;
        this.tooltip = tooltip;
        this.checkEnabled = checkEnabled;
        this.version = version;
        brand(brand.color);
        iconPainter((ui, x, y, color, fill) -> {
            iconX = x;
            iconY = y;
            LinkIcons.draw(ui, brand, x, y, color, fill);
        });
        tooltip(title, tooltip);
    }

    public Brand brandOf() {
        return brand;
    }

    public int iconX() {
        return iconX;
    }

    public int iconY() {
        return iconY;
    }

    public LinkStatus.State state() {
        if (brand.page == null || !checkEnabled.getAsBoolean()) {
            return LinkStatus.State.NONE;
        }
        return LinkStatus.state(brand.page);
    }

    public static int dotColor(LinkStatus.State state) {
        return switch (state) {
            case AVAILABLE -> GREEN;
            case NOT_PUBLISHED -> YELLOW;
            case CHECKING, UNKNOWN -> GREY;
            case NONE -> 0;
        };
    }

    public static Text stateLine(LinkStatus.State state) {
        return switch (state) {
            case AVAILABLE -> Text.translatable("ui.shatter_gui.about.status.available");
            case NOT_PUBLISHED -> Text.translatable("ui.shatter_gui.about.status.not_published");
            case CHECKING -> Text.translatable("ui.shatter_gui.about.status.checking");
            case UNKNOWN -> Text.translatable("ui.shatter_gui.about.status.unknown");
            case NONE -> null;
        };
    }

    @Override
    protected void draw(Ui ui) {
        if (brand.page != null && checkEnabled.getAsBoolean()) {
            LinkStatus.check(version);
        }
        LinkStatus.State state = state();
        Text line = stateLine(state);
        tooltip(title, line == null ? tooltip : Text.translatable("ui.shatter_gui.about.status.tooltip", tooltip, line));
        super.draw(ui);
        int dot = dotColor(state);
        if (dot != 0) {
            if (state == LinkStatus.State.CHECKING) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 2.0e8);
                dot = Theme.fade(dot, pulse);
            }
            Draw.circle(ui, x + w - 5, y + 5, 3, Theme.CARD);
            Draw.circle(ui, x + w - 5, y + 5, 2, dot);
        }
    }
}
