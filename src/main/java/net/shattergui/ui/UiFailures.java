package net.shattergui.ui;

import net.shattergui.ShatterGuiClient;

import java.util.List;

/**
 * The mod's UI failures this session, written once per kind to the game log ({@link FailureLog}). Tests read
 * {@link #count()} and {@link #kinds()}: a clean run has none.
 */
public final class UiFailures {
    private static final FailureLog LOG = new FailureLog((where, error) ->
            ShatterGuiClient.LOGGER.error("[ShatterUi] {} failed; it is shown as an error box and the rest keeps working",
                    where, error));

    private UiFailures() {
    }

    public static void report(String where, Throwable error) {
        LOG.report(where, error);
    }

    public static int count() {
        return LOG.count();
    }

    public static List<String> kinds() {
        return LOG.kinds();
    }

    /** "draw Slider (in Inspector)" — the widget's class and the first named class around it. */
    static String describe(String action, Widget widget) {
        StringBuilder sb = new StringBuilder(action).append(' ').append(name(widget));
        for (Widget p = widget.parent; p != null; p = p.parent) {
            String n = name(p);
            if (!n.equals("Container") && !n.equals("Column") && !n.equals("Row")) {
                sb.append(" (in ").append(n).append(')'); // not shown: the failure log
                break;
            }
        }
        return sb.toString();
    }

    private static String name(Widget widget) {
        Class<?> c = widget.getClass();
        while (c.isAnonymousClass()) {
            c = c.getSuperclass();
        }
        return c.getSimpleName();
    }
}
