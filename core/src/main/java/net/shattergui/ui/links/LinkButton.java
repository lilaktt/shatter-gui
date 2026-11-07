package net.shattergui.ui.links;

import net.shattergui.links.LinkStatus;
import net.shattergui.platform.Text;
import net.shattergui.ui.Button;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.function.BooleanSupplier;

/**
 * A button to one of the mod's pages in "About": its platform's colour fills it smoothly on hover and keyboard focus
 * ({@link Button#brand}), its small icon ({@link LinkIcons}), and a dot for whether the page is up ({@link LinkStatus}):
 * green, yellow (not published yet or in moderation), grey (could not check). No dot for a page that is never checked
 * (CurseForge) or while the check is off.
 */
public final class LinkButton extends Button {
    /** The pages, with their colour and icon file. Bug reports go to GitHub, so they share its colour and state. */
    public enum Brand {
        /** Modrinth's green. */
        MODRINTH(0xFF1BD96A, "modrinth", LinkStatus.Page.MODRINTH),
        /** CurseForge's red-orange. */
        CURSEFORGE(0xFFF16436, "curseforge", null),
        /** A neutral light grey. */
        GITHUB(0xFFE6EDF3, "github", LinkStatus.Page.GITHUB),
        BUG(0xFFE6EDF3, "bug", LinkStatus.Page.GITHUB);

        public final int color;
        /** The icon file name in {@code assets/shatter_gui/textures/gui/links/}. */
        public final String file;
        /** The page whose state the dot shows, or null (never checked). */
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

    /**
     * @param checkEnabled whether the pages may be checked (the {@code linkStatusCheck} setting); the check starts when
     *                     the button is first drawn, so only while "About" is open
     * @param version      the mod's version, for the User-Agent
     */
    public LinkButton(Brand brand, Text title, Text tooltip, BooleanSupplier checkEnabled, String version) {
        super(title, null, Kind.SECONDARY, null);
        this.brand = brand;
        this.title = title;
        this.tooltip = tooltip;
        this.checkEnabled = checkEnabled;
        this.version = version;
        brand(brand.color);
        iconPainter((ui, x, y, color, fill) -> LinkIcons.draw(ui, brand, x, y, color, fill));
        tooltip(title, tooltip);
    }

    public Brand brandOf() {
        return brand;
    }

    /** What the dot shows now. */
    public LinkStatus.State state() {
        if (brand.page == null || !checkEnabled.getAsBoolean()) {
            return LinkStatus.State.NONE;
        }
        return LinkStatus.state(brand.page);
    }

    /** The dot's colour for a state, or 0 for none. */
    public static int dotColor(LinkStatus.State state) {
        return switch (state) {
            case AVAILABLE -> GREEN;
            case NOT_PUBLISHED -> YELLOW;
            case CHECKING, UNKNOWN -> GREY;
            case NONE -> 0;
        };
    }

    /** The sentence the tooltip adds for a state, or null. */
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
            // Once per session, off the render thread; returns at once.
            LinkStatus.check(version);
        }
        LinkStatus.State state = state();
        Text line = stateLine(state);
        tooltip(title, line == null ? tooltip : Text.translatable("ui.shatter_gui.about.status.tooltip", tooltip, line));
        super.draw(ui);
        int dot = dotColor(state);
        if (dot != 0) {
            if (state == LinkStatus.State.CHECKING) {
                // Pulses while the request is under way.
                float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 2.0e8);
                dot = Theme.fade(dot, pulse);
            }
            // A ring of the card's colour keeps it apart from a filled (hovered) button.
            Draw.circle(ui, x + w - 5, y + 5, 3, Theme.CARD);
            Draw.circle(ui, x + w - 5, y + 5, 2, dot);
        }
    }
}
