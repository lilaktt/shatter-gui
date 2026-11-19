package net.shattergui.ui;

import net.shattergui.ShatterMod;

import java.util.List;

public final class UiFailures {
    private static final FailureLog LOG = new FailureLog((where, error) ->
            ShatterMod.LOGGER.error("[ShatterUi] {} failed; it is shown as an error box and the rest keeps working",
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

    static String describe(String action, Widget widget) {
        StringBuilder sb = new StringBuilder(action).append(' ').append(name(widget));
        for (Widget p = widget.parent; p != null; p = p.parent) {
            String n = name(p);
            if (!n.equals("Container") && !n.equals("Column") && !n.equals("Row")) {
                sb.append(" (in ").append(n).append(')');
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
