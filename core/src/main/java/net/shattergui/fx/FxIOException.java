package net.shattergui.fx;

import java.io.IOException;

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

    public String key() {
        return key;
    }

    public Object[] args() {
        return args;
    }
}
