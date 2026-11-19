package net.shattergui.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RecentWarnings {
    public static final int KEPT = 10;
    static final int MAX_LENGTH = 300;
    private static final Deque<String> LAST = new ArrayDeque<>();

    private RecentWarnings() {
    }

    public static synchronized void add(String level, String message) {
        String line = level + " " + (message == null ? "" : message.replace('\n', ' ').replace('\r', ' ').strip());
        if (line.length() > MAX_LENGTH) {
            line = line.substring(0, MAX_LENGTH) + "…";
        }
        LAST.addLast(line);
        while (LAST.size() > KEPT) {
            LAST.removeFirst();
        }
    }

    public static synchronized List<String> all() {
        return new ArrayList<>(LAST);
    }

    public static synchronized void clear() {
        LAST.clear();
    }
}
