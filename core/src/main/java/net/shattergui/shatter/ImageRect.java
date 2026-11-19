package net.shattergui.shatter;

public record ImageRect(double x0, double y0, double x1, double y1) {
    public static final ImageRect FULL = new ImageRect(0, 0, 1, 1);

    public static ImageRect of(double x0, double y0, double x1, double y1) {
        double cx0 = Math.clamp(x0, 0, 1);
        double cy0 = Math.clamp(y0, 0, 1);
        double cx1 = Math.clamp(x1, 0, 1);
        double cy1 = Math.clamp(y1, 0, 1);
        return cx1 > cx0 && cy1 > cy0 ? new ImageRect(cx0, cy0, cx1, cy1) : FULL;
    }

    public double width() {
        return x1 - x0;
    }

    public double height() {
        return y1 - y0;
    }
}
