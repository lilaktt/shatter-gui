package net.shattergui.ui;

public final class Reveal {
    public enum State {
        CLOSED, OPENING, OPEN, CLOSING
    }

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

    public float progress() {
        return progress;
    }

    public float eased() {
        return Anim.easeOut(progress);
    }

    public boolean wantsOpen() {
        return state == State.OPENING || state == State.OPEN;
    }

    public boolean visible() {
        return state != State.CLOSED;
    }

    public boolean open() {
        if (wantsOpen()) {
            return false;
        }
        state = progress >= 1f ? State.OPEN : State.OPENING;
        return true;
    }

    public boolean close() {
        if (!wantsOpen()) {
            return false;
        }
        state = progress <= 0f ? State.CLOSED : State.CLOSING;
        return true;
    }

    public void toggle() {
        if (wantsOpen()) {
            close();
        } else {
            open();
        }
    }

    public void reset() {
        state = State.CLOSED;
        progress = 0f;
    }

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
