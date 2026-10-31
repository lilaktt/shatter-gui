package net.shattergui.ui;

/**
 * Open/close state of a pop-up (a drop-down list, a menu): closed → opening → open → closing → closed. The progress
 * 0..1 is kept when the direction flips, so a second click while it animates turns the animation round smoothly instead
 * of starting it over. Closing is shorter than opening.
 */
public final class Reveal {
    public enum State {
        CLOSED, OPENING, OPEN, CLOSING
    }

    /** Default times: a list opens in 0.16 s and closes in 0.09 s. */
    public static final float OPEN_SECONDS = 0.16f;
    public static final float CLOSE_SECONDS = 0.09f;

    private final float openSeconds;
    private final float closeSeconds;
    private State state = State.CLOSED;
    private float progress;

    public Reveal() {
        this(OPEN_SECONDS, CLOSE_SECONDS);
    }

    public Reveal(float openSeconds, float closeSeconds) {
        this.openSeconds = Math.max(1e-3f, openSeconds);
        this.closeSeconds = Math.max(1e-3f, closeSeconds);
    }

    public State state() {
        return state;
    }

    /** 0 closed … 1 fully open (linear in time). */
    public float progress() {
        return progress;
    }

    /** The progress through an ease-out curve, for drawing (the same curve both ways, so a turn has no jump). */
    public float eased() {
        return Anim.easeOut(progress);
    }

    /** Opening or open: what a click on its header would close. */
    public boolean wantsOpen() {
        return state == State.OPENING || state == State.OPEN;
    }

    /** Anything to draw: not fully closed. */
    public boolean visible() {
        return state != State.CLOSED;
    }

    /** Starts (or turns round to) opening; nothing if it is already opening or open. True if it changed. */
    public boolean open() {
        if (wantsOpen()) {
            return false;
        }
        state = progress >= 1f ? State.OPEN : State.OPENING;
        return true;
    }

    /** Starts (or turns round to) closing; nothing if it is already closing or closed. True if it changed. */
    public boolean close() {
        if (!wantsOpen()) {
            return false;
        }
        state = progress <= 0f ? State.CLOSED : State.CLOSING;
        return true;
    }

    /** Open if it is closing or closed, close if it is opening or open. */
    public void toggle() {
        if (wantsOpen()) {
            close();
        } else {
            open();
        }
    }

    /** Jumps to closed (removed from the screen by another path). */
    public void reset() {
        state = State.CLOSED;
        progress = 0f;
    }

    /** Advances by {@code dt} seconds and returns the new state. */
    public State tick(float dt) {
        if (!(dt > 0f)) {
            return state;
        }
        switch (state) {
            case OPENING -> {
                progress = Math.min(1f, progress + dt / openSeconds);
                if (progress >= 1f) {
                    state = State.OPEN;
                }
            }
            case CLOSING -> {
                progress = Math.max(0f, progress - dt / closeSeconds);
                if (progress <= 0f) {
                    state = State.CLOSED;
                }
            }
            default -> {
            }
        }
        return state;
    }
}
