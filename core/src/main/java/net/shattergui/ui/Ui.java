package net.shattergui.ui;

import net.shattergui.platform.GuiDrawing;
import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;
import net.shattergui.platform.UiFont;
import net.shattergui.platform.UiRect;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * What a widget draws with during one frame: the graphics, the font, the mouse, the widget under the mouse and the
 * current clip rectangle. Clipping always goes through {@link #pushClip}/{@link #popClip} so that the editor's own
 * textured triangles ({@link Draw#triangles}) are clipped exactly like the vanilla elements.
 */
public final class Ui {
    public final GuiDrawing g;
    public final UiFont font;
    public final int mouseX;
    public final int mouseY;
    /** The topmost widget under the mouse (null if none or the mouse is over a modal layer). */
    final Widget hovered;
    final Widget focused;
    private final Deque<UiRect> clips = new ArrayDeque<>();
    private Widget tooltipOwner;

    Ui(GuiDrawing g, int mouseX, int mouseY, Widget hovered, Widget focused) {
        this.g = g;
        this.font = Platform.get().texts().font();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hovered = hovered;
        this.focused = focused;
    }

    public boolean isHovered(Widget widget) {
        return hovered == widget;
    }

    /** The mouse is over this widget or one of its children. */
    public boolean isWithin(Widget widget) {
        for (Widget w = hovered; w != null; w = w.parent) {
            if (w == widget) {
                return true;
            }
        }
        return false;
    }

    public boolean isFocused(Widget widget) {
        return focused == widget;
    }

    /** Clips to the intersection of the current clip and this rectangle until {@link #popClip}. */
    public void pushClip(int x, int y, int w, int h) {
        UiRect rect = new UiRect(x, y, Math.max(0, w), Math.max(0, h));
        UiRect current = clips.peek();
        if (current != null) {
            UiRect cut = current.intersection(rect);
            rect = cut != null ? cut : new UiRect(x, y, 0, 0);
        }
        clips.push(rect);
        g.enableScissor(rect.left(), rect.top(), rect.right(), rect.bottom());
    }

    public void popClip() {
        clips.pop();
        g.disableScissor();
    }

    /** What a widget's drawing may leave changed: the clip depth and the transform. */
    public record State(int clips, Object pose) {
    }

    public State save() {
        return new State(clips.size(), g.pose().snapshot());
    }

    /** Back to a saved state after a drawing failed half-way (closes its clips, resets the transform). */
    public void restore(State state) {
        while (clips.size() > state.clips()) {
            popClip();
        }
        g.pose().restore(state.pose());
    }

    /** The current clip, or null if nothing is clipped. */
    public UiRect clip() {
        return clips.peek();
    }

    /** True if the point is inside the current clip (or nothing is clipped). */
    public boolean inClip(double x, double y) {
        UiRect clip = clips.peek();
        return clip == null || clip.containsPoint((int) Math.floor(x), (int) Math.floor(y));
    }

    // --- text ---

    public void text(Text text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    public void text(String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    /** Text cut to {@code width} with an ellipsis. */
    /** Texts shortened with … since {@link #trackTruncation} was turned on (tests), or null when off. */
    private static java.util.Set<String> truncated;

    /** Starts (true, with an empty set) or stops recording which texts had to be shortened. */
    public static void trackTruncation(boolean on) {
        truncated = on ? java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>()) : null;
    }

    /** The texts shortened while tracking (a copy), empty when not tracking. */
    public static java.util.Set<String> truncated() {
        return truncated == null ? java.util.Set.of() : java.util.Set.copyOf(truncated);
    }

    private static void noteTruncated(String text) {
        if (truncated != null) {
            truncated.add(text);
        }
    }

    public void textFit(Text text, int x, int y, int width, int color) {
        String s = text.getString();
        if (font.width(s) <= width) {
            g.text(font, text, x, y, color, false);
        } else {
            noteTruncated(s);
            g.text(font, font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…", x, y, color, false);
        }
    }

    public void textCentered(Text text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /**
     * The size of small text, chosen so each font pixel is a whole number of screen pixels (a pixel font scaled by
     * anything else turns blurry or unreadable): GUI scale 1–2 → 1, 3 → 2/3, 4 → 3/4, 5 → 4/5 …
     */
    public static float smallScale() {
        double gui = Platform.get().desktop().guiScale();
        return gui <= 2 ? 1f : (float) ((Math.round(gui) - 1) / (double) Math.round(gui));
    }

    /** Smaller text for secondary lines like technical ids (see {@link #smallScale}). */
    public void textSmall(String text, int x, int y, int width, int color) {
        float scale = smallScale();
        int max = (int) (width / scale);
        if (font.width(text) > max) {
            noteTruncated(text);
        }
        String s = font.width(text) <= max ? text : font.plainSubstrByWidth(text, Math.max(0, max - font.width("…"))) + "…";
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** Wrapped text; returns its height. */
    public int textWrapped(Text text, int x, int y, int width, int color) {
        var lines = font.split(text, Math.max(10, width));
        int yy = y;
        for (var line : lines) {
            g.text(font, line, x, yy, color, false);
            yy += Theme.LINE;
        }
        return yy - y;
    }

    public int wrappedHeight(Text text, int width) {
        return font.split(text, Math.max(10, width)).size() * Theme.LINE;
    }

    void setTooltipOwner(Widget widget) {
        tooltipOwner = widget;
    }

    Widget tooltipOwner() {
        return tooltipOwner;
    }
}
