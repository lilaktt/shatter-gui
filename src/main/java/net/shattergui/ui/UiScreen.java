package net.shattergui.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A screen made of {@link Widget}s: lays out and draws its root every frame, then the overlays (drop-down lists,
 * dialogs) above it and the tooltip above everything. Owns focus (Tab/Shift+Tab, arrows when the focused widget does
 * not use them), mouse capture while a button is held, and a minimum size below which it only shows a notice.
 */
public abstract class UiScreen extends Screen {
    public static final int MIN_WIDTH = 400;
    public static final int MIN_HEIGHT = 230;

    private static UiScreen active;

    protected final Container root = new Container();
    private final List<Overlay> overlays = new ArrayList<>();
    private Widget focused;
    private Widget pressed;
    private Widget lastHovered;
    private double hoverSince;
    private final Anim tooltipAlpha = new Anim(0f, 12f);
    /** Focus was last moved with the keyboard (its tooltip shows); a click ends that. */
    private boolean keyboardFocus;
    private double focusSince;

    /** A layer above the root: a drop-down list or a dialog. */
    public abstract static class Overlay extends Container {
        /** Blocks everything below (dialogs). */
        public boolean modal() {
            return false;
        }

        /** A click outside: by default the overlay closes. */
        public void outsideClick() {
            UiScreen.active().closeOverlay(this);
        }

        /** Places the overlay for the current screen size. */
        public abstract void place(int screenWidth, int screenHeight);

        /** Esc while it is on top; by default closes it. */
        public void escape() {
            UiScreen.active().closeOverlay(this);
        }

        /** After it was removed, by any path. */
        protected void onClosed() {
        }

        /**
         * A click here belongs to the overlay's own opener (a drop-down's header, a menu's button): it is not an outside
         * click, so the opener can toggle it instead of it closing and at once reopening.
         */
        public boolean ownsClick(double mx, double my) {
            return false;
        }

        /** Animating out: still drawn, but takes no input and is not the top overlay. */
        public boolean closing() {
            return false;
        }
    }

    protected UiScreen(Component title) {
        super(title);
    }

    /** The UiScreen being shown (widgets use it to open overlays, move focus, play sounds). */
    public static UiScreen active() {
        return active;
    }

    /** Lays out {@link #root} for the current size (called every frame). */
    protected abstract void layoutRoot(int width, int height);

    /** Esc when no overlay is open: by default closes the screen. */
    protected void escape() {
        onClose();
    }

    public boolean tooSmall() {
        return width < MIN_WIDTH || height < MIN_HEIGHT;
    }

    // --- overlays and focus ---

    public void openOverlay(Overlay overlay) {
        try {
            overlay.place(width, height);
        } catch (RuntimeException e) {
            // A dialog or list that cannot even be placed is not opened.
            overlay.markBroken(e, "open");
            return;
        }
        overlays.add(overlay);
    }

    public void closeOverlay(Overlay overlay) {
        if (!overlays.remove(overlay)) {
            return;
        }
        try {
            overlay.onClosed();
        } catch (RuntimeException e) {
            reportScreen("close overlay", e);
        }
        if (focused != null && isInside(focused, overlay)) {
            focused = null;
        }
    }

    /** The root of the widget tree (tests walk it). */
    public Container rootWidget() {
        return root;
    }

    /** Whether an overlay is open (one that is only animating out does not count). */
    public boolean hasOverlay() {
        return topOverlay() != null;
    }

    /** The topmost overlay that is not closing, or null. */
    public Overlay topOverlay() {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (!overlays.get(i).closing()) {
                return overlays.get(i);
            }
        }
        return null;
    }

    public List<Overlay> overlays() {
        return overlays;
    }

    public Widget focused() {
        return focused;
    }

    public void setFocus(Widget widget) {
        if (widget == focused) {
            return;
        }
        Widget old = focused;
        focused = widget;
        if (old != null) {
            guard(old, "focus", () -> {
                old.focusChanged(false);
                return true;
            });
        }
        if (widget != null) {
            guard(widget, "focus", () -> {
                widget.focusChanged(true);
                return true;
            });
            revealed(widget);
        }
    }

    /** Called when focus moves to a widget (scroll views bring it into sight). */
    protected void revealed(Widget widget) {
        for (Widget p = widget.parent; p != null; p = p.parent) {
            if (p instanceof ScrollView scroll) {
                scroll.reveal(widget);
            }
        }
    }

    private static boolean isInside(Widget widget, Widget ancestor) {
        for (Widget w = widget; w != null; w = w.parent) {
            if (w == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** Widgets Tab can reach: those of the top modal overlay, otherwise of the top overlay and the root. */
    private List<Widget> focusables() {
        List<Widget> out = new ArrayList<>();
        Overlay top = topOverlay();
        if (top != null && top.modal()) {
            top.collectFocusable(out);
            return out;
        }
        root.collectFocusable(out);
        if (top != null) {
            top.collectFocusable(out);
        }
        return out;
    }

    public void moveFocus(boolean forward) {
        List<Widget> list = focusables();
        if (list.isEmpty()) {
            return;
        }
        int index = list.indexOf(focused);
        int next = index < 0 ? (forward ? 0 : list.size() - 1) : Math.floorMod(index + (forward ? 1 : -1), list.size());
        setFocus(list.get(next));
        keyboardFocus = true;
        focusSince = UiClock.time();
    }

    // --- frame ---

    @Override
    protected void init() {
        active = this;
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
        super.removed();
    }

    private Widget hitTest(double mx, double my) {
        if (!MouseGuard.valid(mx, my)) {
            return null;
        }
        try {
            for (int i = overlays.size() - 1; i >= 0; i--) {
                Overlay overlay = overlays.get(i);
                if (overlay.closing()) {
                    continue;
                }
                Widget hit = overlay.hit(mx, my);
                if (hit != null) {
                    return outermostBroken(hit);
                }
                if (overlay.modal()) {
                    return null;
                }
            }
            return tooSmall() ? null : outermostBroken(root.hit(mx, my));
        } catch (RuntimeException e) {
            reportScreen("hit test", e);
            return null;
        }
    }

    /** A widget inside a broken one counts as the broken one (its error box), which takes no input. */
    private static Widget outermostBroken(Widget hit) {
        Widget result = hit;
        for (Widget w = hit; w != null; w = w.parent) {
            if (w.broken) {
                result = w;
            }
        }
        return result;
    }

    /**
     * Runs a widget's handler; if it throws, the widget becomes an error box (logged once) and the event counts as
     * handled. A broken widget handles nothing.
     */
    private static boolean guard(Widget widget, String action, java.util.function.BooleanSupplier handler) {
        if (widget.broken) {
            return false;
        }
        try {
            return handler.getAsBoolean();
        } catch (RuntimeException e) {
            widget.markBroken(e, action);
            return true;
        }
    }

    /** A failure of the screen itself (not of one widget): logged once per kind, the screen goes on. */
    protected void reportScreen(String action, RuntimeException e) {
        UiFailures.report(action + " " + getClass().getSimpleName(), e);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int rawMouseX, int rawMouseY, float partialTick) {
        active = this;
        UiClock.frame();
        // A position that is not one (NaN or infinity from another mod): no mouse, and a drag in progress ends.
        boolean mouse = MouseGuard.valid(rawMouseX, rawMouseY);
        int mouseX = mouse ? rawMouseX : MouseGuard.NONE;
        int mouseY = mouse ? rawMouseY : MouseGuard.NONE;
        if (!mouse) {
            cancelPress();
        }
        try {
            if (tooSmall()) {
                Ui ui = new Ui(graphics, mouseX, mouseY, null, null);
                drawTooSmall(ui);
                return;
            }
            root.setBounds(0, 0, width, height);
            try {
                layoutRoot(width, height);
            } catch (RuntimeException e) {
                reportScreen("layout", e);
            }
            List<Overlay> broken = new ArrayList<>();
            for (Overlay overlay : overlays) {
                try {
                    overlay.place(width, height);
                } catch (RuntimeException e) {
                    overlay.markBroken(e, "place");
                    broken.add(overlay);
                }
            }
            broken.forEach(this::closeOverlay);
            if (pressed != null && pressed.broken) {
                pressed = null;
            }
            Widget hovered = !mouse ? null : pressed != null ? (pressed.contains(mouseX, mouseY) ? pressed : null)
                    : hitTest(mouseX, mouseY);
            if (hovered != lastHovered) {
                lastHovered = hovered;
                hoverSince = UiClock.time();
            }
            Ui ui = new Ui(graphics, mouseX, mouseY, hovered, focused);
            root.render(ui);
            for (Overlay overlay : List.copyOf(overlays)) {
                graphics.nextStratum();
                if (overlay.modal()) {
                    Draw.rect(ui, 0, 0, width, height, 0x88000000);
                }
                overlay.render(ui);
            }
            Ui.State state = ui.save();
            try {
                drawTooltip(ui, hovered);
            } catch (RuntimeException e) {
                ui.restore(state);
                reportScreen("tooltip", e);
            }
        } catch (RuntimeException e) {
            reportScreen("draw", e);
        }
    }

    /** Ends a press/drag without a release (the mouse became unusable). */
    private void cancelPress() {
        if (pressed != null) {
            Widget w = pressed;
            pressed = null;
            guard(w, "mouse up", () -> {
                w.mouseUp(MouseGuard.NONE, MouseGuard.NONE, 0);
                return true;
            });
        }
    }

    /** Shown instead of the UI when the window is smaller than {@link #MIN_WIDTH}×{@link #MIN_HEIGHT}. */
    protected void drawTooSmall(Ui ui) {
        Draw.rect(ui, 0, 0, width, height, Theme.BACKDROP_BOTTOM);
        int boxW = Math.min(width - 16, 260);
        Component text = Component.translatable("ui.shatter_gui.too_small", MIN_WIDTH, MIN_HEIGHT);
        int textH = ui.wrappedHeight(text, boxW - 16);
        int boxH = textH + 16;
        int bx = (width - boxW) / 2;
        int by = (height - boxH) / 2;
        Draw.roundBox(ui, bx, by, boxW, boxH, Theme.RADIUS_CARD, Theme.CARD, Theme.BORDER);
        ui.textWrapped(text, bx + 8, by + 8, boxW - 16, Theme.TEXT);
    }

    /** The widget whose tooltip explains {@code widget}: itself, or the nearest parent with one (a field's row). */
    public static Widget tooltipOwner(Widget widget) {
        for (Widget w = widget; w != null; w = w.parent) {
            if ((w == widget || w.tooltipCoversChildren()) && (w.tooltipTitle() != null || w.tooltipText() != null)) {
                return w;
            }
        }
        return null;
    }

    private void drawTooltip(Ui ui, Widget hovered) {
        Widget owner = tooltipOwner(hovered);
        int anchorX = ui.mouseX;
        int anchorY = ui.mouseY;
        boolean show = owner != null && pressed == null && UiClock.time() - hoverSince > Theme.TOOLTIP_DELAY;
        if (!show && keyboardFocus && focused != null && pressed == null && UiClock.time() - focusSince > Theme.TOOLTIP_DELAY) {
            // Moved here with the keyboard: the focused control's tooltip, under it.
            owner = tooltipOwner(focused);
            show = owner != null;
            anchorX = focused.x;
            anchorY = focused.y + focused.h - 8;
        }
        float alpha = tooltipAlpha.target(show).tick();
        if (!show || alpha < 0.02f) {
            return;
        }
        ui.g.nextStratum();
        int maxW = Math.min(220, width / 2);
        Component title = owner.tooltipTitle() == null ? null
                : owner.tooltipTitle().copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        Component text = owner.tooltipText();
        int contentW = 0;
        List<net.minecraft.util.FormattedCharSequence> titleLines = title == null ? List.of() : ui.font.split(title, maxW);
        for (var line : titleLines) {
            contentW = Math.max(contentW, ui.font.width(line));
        }
        List<net.minecraft.util.FormattedCharSequence> lines = text == null ? List.of() : ui.font.split(text, maxW);
        for (var line : lines) {
            contentW = Math.max(contentW, ui.font.width(line));
        }
        int boxW = contentW + 12;
        int boxH = 8 + titleLines.size() * Theme.LINE + (!titleLines.isEmpty() && !lines.isEmpty() ? 3 : 0)
                + lines.size() * Theme.LINE;
        int bx = anchorX + 10;
        int by = anchorY + 12;
        if (bx + boxW > width - 4) {
            bx = Math.max(4, anchorX - boxW - 6);
        }
        if (by + boxH > height - 4) {
            by = Math.max(4, anchorY - boxH - 6);
        }
        // Never past the screen's edges, even when it is wider or taller than the room on either side.
        bx = Math.clamp(bx, 4, Math.max(4, width - 4 - boxW));
        by = Math.clamp(by, 4, Math.max(4, height - 4 - boxH));
        float rise = (1f - alpha) * 3f;
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, rise);
        Draw.shadow(ui, bx, by, boxW, boxH, Theme.RADIUS_CONTROL, alpha);
        Draw.roundBox(ui, bx, by, boxW, boxH, Theme.RADIUS_CONTROL, Theme.fade(0xF5101116, alpha),
                Theme.fade(Theme.accentAlpha(0xA0), alpha));
        int ty = by + 5;
        for (var line : titleLines) {
            ui.g.text(ui.font, line, bx + 6, ty, Theme.fade(Theme.TEXT, alpha), false);
            ty += Theme.LINE;
        }
        if (!titleLines.isEmpty()) {
            ty += 3;
        }
        for (var line : lines) {
            ui.g.text(ui.font, line, bx + 6, ty, Theme.fade(Theme.TEXT_DIM, alpha), false);
            ty += Theme.LINE;
        }
        ui.g.pose().popMatrix();
    }

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (tooSmall() || !MouseGuard.valid(mx, my)) {
            return true;
        }
        keyboardFocus = false;
        Overlay top = topOverlay();
        if (top != null) {
            if (top.hit(mx, my) == null && !top.ownsClick(mx, my)) {
                guard(top, "outside click", () -> {
                    top.outsideClick();
                    return true;
                });
                if (top.modal()) {
                    return true;
                }
                // A non-modal overlay closed: the click also goes to what is under it.
            }
        }
        Widget hit = hitTest(mx, my);
        Widget focusTarget = null;
        for (Widget w = hit; w != null; w = w.parent) {
            if (w.enabled && !w.broken && w.focusable()) {
                focusTarget = w;
                break;
            }
        }
        setFocus(focusTarget);
        if (doubleClick && event.button() == 0) {
            for (Widget w = hit; w != null; w = w.parent) {
                Widget target = w;
                if (w.enabled && guard(w, "double click", () -> target.doubleClicked(mx, my))) {
                    return true;
                }
            }
        }
        for (Widget w = hit; w != null; w = w.parent) {
            Widget target = w;
            if (w.enabled && guard(w, "mouse down", () -> target.mouseDown(mx, my, event.button()))) {
                pressed = w.broken ? null : w;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed != null) {
            Widget w = pressed;
            pressed = null;
            double mx = MouseGuard.valid(event.x()) ? event.x() : MouseGuard.NONE;
            double my = MouseGuard.valid(event.y()) ? event.y() : MouseGuard.NONE;
            guard(w, "mouse up", () -> {
                w.mouseUp(mx, my, event.button());
                return true;
            });
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed != null) {
            if (!MouseGuard.valid(event.x(), event.y())) {
                cancelPress();
                return true;
            }
            Widget w = pressed;
            guard(w, "drag", () -> {
                w.mouseDrag(event.x(), event.y());
                return true;
            });
            if (w.broken) {
                pressed = null;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!MouseGuard.valid(mx, my) || !Double.isFinite(vertical)) {
            return true;
        }
        for (Widget w = hitTest(mx, my); w != null; w = w.parent) {
            Widget target = w;
            if (guard(w, "scroll", () -> target.scroll(mx, my, vertical))) {
                return true;
            }
        }
        return true;
    }

    /**
     * Screen-wide shortcuts (Ctrl+Z, Ctrl+S, …), asked before the focused widget so a text field does not swallow
     * them. True if handled.
     */
    protected boolean shortcut(KeyEvent event) {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        try {
            if (!tooSmall() && shortcut(event)) {
                return true;
            }
        } catch (RuntimeException e) {
            reportScreen("shortcut", e);
            return true;
        }
        Widget target = focused;
        if (target != null && target.enabled && guard(target, "key", () -> target.key(event))) {
            return true;
        }
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            Overlay top = topOverlay();
            if (top != null) {
                if (top.broken) {
                    closeOverlay(top);
                } else {
                    guard(top, "escape", () -> {
                        top.escape();
                        return true;
                    });
                    if (top.broken) {
                        closeOverlay(top);
                    }
                }
            } else {
                try {
                    escape();
                } catch (RuntimeException e) {
                    reportScreen("escape", e);
                }
            }
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            moveFocus(!event.hasShiftDown());
            return true;
        }
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
            moveFocus(key == InputConstants.KEY_DOWN);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        Widget target = focused;
        return target != null && target.enabled && guard(target, "typing", () -> target.typed(event));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
