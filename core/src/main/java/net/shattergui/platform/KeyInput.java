package net.shattergui.platform;

/**
 * A key press as the game reports it (key codes are GLFW's, see {@link Keys}). The answers are those of 26.1's key
 * events, which a port must reproduce: the modifier checks read the event's own modifier bits; selection is Enter,
 * keypad Enter or Space; confirmation is Enter or keypad Enter; select all / copy / cut / paste are Ctrl+A/C/X/V
 * without Shift and Alt (Cmd instead of Ctrl on macOS).
 */
public interface KeyInput {
    int key();

    boolean hasShiftDown();

    /** The Control key (also on macOS). */
    boolean hasControlDown();

    /** Space or Enter (a button is pressed). */
    boolean isSelection();

    /** Enter. */
    boolean isConfirmation();

    boolean isUp();

    boolean isDown();

    boolean isLeft();

    boolean isRight();

    boolean isSelectAll();

    boolean isCopy();

    boolean isCut();

    boolean isPaste();
}
