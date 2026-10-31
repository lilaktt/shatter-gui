package net.shattergui.fx;

import java.io.IOException;

/**
 * A file problem the player can be told about in their language: {@code ui.shatter_gui.fx.io.<key>} with
 * {@code args}. The English message stays for the log.
 */
public final class FxIOException extends IOException {
    private final String key;
    private final transient Object[] args;

    public FxIOException(String key, String english, Object... args) {
        super(english);
        this.key = key;
        this.args = args;
    }

    public FxIOException(String key, String english, Throwable cause, Object... args) {
        super(english, cause);
        this.key = key;
        this.args = args;
    }

    /** The translation key part after {@code ui.shatter_gui.fx.io.}. */
    public String key() {
        return key;
    }

    public Object[] args() {
        return args;
    }
}
