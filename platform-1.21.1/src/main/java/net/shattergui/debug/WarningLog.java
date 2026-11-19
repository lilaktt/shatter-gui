package net.shattergui.debug;

import net.shattergui.ShatterMod;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

public final class WarningLog {
    private static boolean installed;

    private WarningLog() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            String name = ShatterMod.LOGGER.getName();
            AbstractAppender appender = new AbstractAppender("ShatterGuiRecentWarnings", null, null, true,
                    Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (name.equals(event.getLoggerName()) && event.getLevel().isMoreSpecificThan(Level.WARN)) {
                        Throwable thrown = event.getThrown();
                        RecentWarnings.add(event.getLevel().name(), event.getMessage().getFormattedMessage()
                                + (thrown != null ? " (" + thrown + ")" : ""));
                    }
                }
            };
            appender.start();
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        } catch (Throwable t) {
            ShatterMod.LOGGER.debug("Recent warnings are not collected", t);
        }
    }
}
