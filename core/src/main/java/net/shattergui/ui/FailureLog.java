package net.shattergui.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Remembers the UI failures of this session: every one is counted, but each kind (where + what went wrong) is
 * written to the log only once, so a widget failing on every frame or every rebuild does not flood it. Pure code.
 */
public final class FailureLog {
    private static final int MAX_KEPT = 200;
    private final Set<String> logged = new LinkedHashSet<>();
    private final List<String> all = new ArrayList<>();
    private final BiConsumer<String, Throwable> sink;
    private int count;

    /** @param sink writes one line (and the exception) to the log */
    public FailureLog(BiConsumer<String, Throwable> sink) {
        this.sink = sink;
    }

    /**
     * A failure in {@code where} (e.g. "draw Slider"). Logged if this kind was not logged before; returns true if it
     * was logged now.
     */
    public synchronized boolean report(String where, Throwable error) {
        count++;
        String signature = where + " | " + error.getClass().getName() + ": " + error.getMessage();
        if (all.size() < MAX_KEPT) {
            all.add(signature);
        }
        if (logged.add(signature)) {
            sink.accept(where, error);
            return true;
        }
        return false;
    }

    /** Failures reported so far (repeats included). */
    public synchronized int count() {
        return count;
    }

    /** The distinct kinds logged so far. */
    public synchronized List<String> kinds() {
        return List.copyOf(logged);
    }

    public synchronized List<String> recent() {
        return List.copyOf(all);
    }
}
