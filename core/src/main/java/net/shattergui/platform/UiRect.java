package net.shattergui.platform;

public record UiRect(int left, int top, int width, int height) {
    public int right() {
        return left + width;
    }

    public int bottom() {
        return top + height;
    }

    public UiRect intersection(UiRect other) {
        int l = Math.max(left(), other.left());
        int t = Math.max(top(), other.top());
        int r = Math.min(right(), other.right());
        int b = Math.min(bottom(), other.bottom());
        return l < r && t < b ? new UiRect(l, t, r - l, b - t) : null;
    }

    public boolean containsPoint(int x, int y) {
        return x >= left() && x < right() && y >= top() && y < bottom();
    }
}
