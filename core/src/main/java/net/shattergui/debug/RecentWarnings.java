package net.shattergui.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The mod's last warnings and errors of this session (its log at WARN and above), for the diagnostics the player copies
 * from "About". The platform feeds it from the game's logging; nothing is written anywhere.
 */
public final class RecentWarnings {
    /** How many are kept (the newest). */
    public static final int KEPT = 10;
    /** Longest kept message; the rest is cut. */
    static final int MAX_LENGTH = 300;
    private static final Deque<String> LAST = new ArrayDeque<>();

    private RecentWarnings() {
    }

    /** {@code level}: "WARN", "ERROR" …; {@code message}: the formatted message (and the exception, if any). */
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

    /** Oldest first. */
    public static synchronized List<String> all() {
        return new ArrayList<>(LAST);
    }

    public static synchronized void clear() {
        LAST.clear();
    }
}
