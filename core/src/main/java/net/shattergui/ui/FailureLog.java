package net.shattergui.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class FailureLog {
    private static final int MAX_KEPT = 200;
    private final Set<String> logged = new LinkedHashSet<>();
    private final List<String> all = new ArrayList<>();
    private final BiConsumer<String, Throwable> sink;
    private int count;

    public FailureLog(BiConsumer<String, Throwable> sink) {
        this.sink = sink;
    }

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

    public synchronized int count() {
        return count;
    }

    public synchronized List<String> kinds() {
        return List.copyOf(logged);
    }

    public synchronized List<String> recent() {
        return List.copyOf(all);
    }
}
