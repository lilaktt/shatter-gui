package net.shattergui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * What a widget draws with during one frame: the graphics, the font, the mouse, the widget under the mouse and the
 * current clip rectangle. Clipping always goes through {@link #pushClip}/{@link #popClip} so that the editor's own
 * textured triangles ({@link Draw#triangles}) are clipped exactly like the vanilla elements.
 */
public final class Ui {
    public final GuiGraphicsExtractor g;
    public final Font font;
    public final int mouseX;
    public final int mouseY;
    /** The topmost widget under the mouse (null if none or the mouse is over a modal layer). */
    final Widget hovered;
    final Widget focused;
    private final Deque<ScreenRectangle> clips = new ArrayDeque<>();
    private Widget tooltipOwner;

    Ui(GuiGraphicsExtractor g, int mouseX, int mouseY, Widget hovered, Widget focused) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
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
        ScreenRectangle rect = new ScreenRectangle(x, y, Math.max(0, w), Math.max(0, h));
        ScreenRectangle current = clips.peek();
        if (current != null) {
            ScreenRectangle cut = current.intersection(rect);
            rect = cut != null ? cut : new ScreenRectangle(x, y, 0, 0);
        }
        clips.push(rect);
        g.enableScissor(rect.left(), rect.top(), rect.right(), rect.bottom());
    }

    public void popClip() {
        clips.pop();
        g.disableScissor();
    }

    /** What a widget's drawing may leave changed: the clip depth and the transform. */
    public record State(int clips, org.joml.Matrix3x2f pose) {
    }

    public State save() {
        return new State(clips.size(), new org.joml.Matrix3x2f(g.pose()));
    }

    /** Back to a saved state after a drawing failed half-way (closes its clips, resets the transform). */
    public void restore(State state) {
        while (clips.size() > state.clips()) {
            popClip();
        }
        g.pose().set(state.pose());
    }

    /** The current clip, or null if nothing is clipped. */
    public ScreenRectangle clip() {
        return clips.peek();
    }

    /** True if the point is inside the current clip (or nothing is clipped). */
    public boolean inClip(double x, double y) {
        ScreenRectangle clip = clips.peek();
        return clip == null || clip.containsPoint((int) Math.floor(x), (int) Math.floor(y));
    }

    // --- text ---

    public void text(Component text, int x, int y, int color) {
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

    public void textFit(Component text, int x, int y, int width, int color) {
        String s = text.getString();
        if (font.width(s) <= width) {
            g.text(font, text, x, y, color, false);
        } else {
            noteTruncated(s);
            g.text(font, font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…", x, y, color, false);
        }
    }

    public void textCentered(Component text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /**
     * The size of small text, chosen so each font pixel is a whole number of screen pixels (a pixel font scaled by
     * anything else turns blurry or unreadable): GUI scale 1–2 → 1, 3 → 2/3, 4 → 3/4, 5 → 4/5 …
     */
    public static float smallScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
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
    public int textWrapped(Component text, int x, int y, int width, int color) {
        var lines = font.split(text, Math.max(10, width));
        int yy = y;
        for (var line : lines) {
            g.text(font, line, x, yy, color, false);
            yy += Theme.LINE;
        }
        return yy - y;
    }

    public int wrappedHeight(Component text, int width) {
        return font.split(text, Math.max(10, width)).size() * Theme.LINE;
    }

    void setTooltipOwner(Widget widget) {
        tooltipOwner = widget;
    }

    Widget tooltipOwner() {
        return tooltipOwner;
    }
}
