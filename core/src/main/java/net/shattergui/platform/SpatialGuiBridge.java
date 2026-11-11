package net.shattergui.platform;

public interface SpatialGuiBridge {
    enum Reason {
        NOT_INSTALLED("not installed", "nothing to bridge"),
        READY(null, null),
        MISSING(null, "this version lacks: "),
        FAILED(null, "error: "),
        MOD_OFF(null, "Shatter GUI is switched off"),
        SETTING_OFF(null, "turned off in settings (spatialGuiIntegration)"),
        UNSUPPORTED_PLATFORM("not checked", "no Spatial GUI bridge on this platform");

        private final String versionText;
        private final String text;

        Reason(String versionText, String text) {
            this.versionText = versionText;
            this.text = text;
        }

        public String key() {
            return "ui.shatter_gui.about.spatial." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    record Status(String version, boolean enabled, Reason reason, String detail) {
        public Status {
            detail = detail == null ? "" : detail.strip();
        }

        public static Status notInstalled() {
            return new Status(null, false, Reason.NOT_INSTALLED, "");
        }

        public String versionText() {
            return version != null ? version : reason.versionText != null ? reason.versionText : "unknown";
        }

        public String why() {
            if (reason.text == null) {
                return detail;
            }
            return reason.text.endsWith(": ") ? reason.text + detail : reason.text;
        }

        public String summary() {
            return versionText() + ", bridge " + (enabled ? "enabled" : "disabled") + ", " + why();
        }

        public String line() {
            return "Spatial GUI: " + summary();
        }
    }

    void cancelPending();

    default Status status() {
        return new Status(null, false, Reason.UNSUPPORTED_PLATFORM, "");
    }
}
