package net.shattergui.ui.preview;

/**
 * Paints the sample interfaces (176×166, like vanilla) pixel by pixel: a chest, a furnace, the player's inventory or
 * a crafting table, all with the player's inventory and hotbar and a few simple item pictures. Pure code on a {@link Canvas}, so it can
 * be checked without the game. Every pixel and rectangle goes through {@link #fill}, which clips to the canvas: a
 * rectangle partly outside is cut, one entirely outside (or empty) draws nothing. Clipped drawing is counted
 * ({@link #clipped}), so a test can require the designs to fit exactly.
 */
public final class SamplePainter {
    /** Something that takes ARGB pixels. */
    public interface Canvas {
        int width();

        int height();

        /** Only called with coordinates inside the canvas. */
        void set(int x, int y, int argb);
    }

    /** A plain ARGB array (tests, and the fallback when the game image cannot be made). */
    public static final class ArrayCanvas implements Canvas {
        public final int width;
        public final int height;
        public final int[] pixels;

        public ArrayCanvas(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[Math.max(0, width) * Math.max(0, height)];
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void set(int x, int y, int argb) {
            pixels[y * width + x] = argb;
        }
    }

    public static final int WIDTH = 176;
    public static final int HEIGHT = 166;

    private final Canvas canvas;
    private int clipped;

    public SamplePainter(Canvas canvas) {
        this.canvas = canvas;
    }

    /** How many draws were (partly) outside the canvas and cut. */
    public int clipped() {
        return clipped;
    }

    /**
     * The one way anything is drawn: the rectangle cut to the canvas. Empty or fully outside: nothing happens. Any
     * cut is counted.
     */
    public void fill(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        long x1 = Math.min((long) canvas.width(), (long) x + w);
        long y1 = Math.min((long) canvas.height(), (long) y + h);
        if (x0 != x || y0 != y || x1 != (long) x + w || y1 != (long) y + h) {
            clipped++;
        }
        for (int yy = y0; yy < y1; yy++) {
            for (int xx = x0; xx < x1; xx++) {
                canvas.set(xx, yy, argb);
            }
        }
    }

    private void pixel(int x, int y, int argb) {
        fill(x, y, 1, 1, argb);
    }

    /** Paints one of the interfaces over the whole canvas. */
    public void paint(SampleImage.Kind kind) {
        int panel = 0xFFC6C6C6;
        fill(0, 0, WIDTH, HEIGHT, 0x00000000);
        // Panel with rounded 3-pixel corners: dark outline, white light edge top-left, grey shade bottom-right.
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (corner(x, y)) {
                    continue;
                }
                int c = panel;
                boolean edge = x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1 || corner(x - 1, y) || corner(x + 1, y)
                        || corner(x, y - 1) || corner(x, y + 1);
                if (edge) {
                    c = 0xFF000000;
                } else if (x <= 2 || y <= 2) {
                    c = 0xFFFFFFFF;
                } else if (x >= WIDTH - 3 || y >= HEIGHT - 3) {
                    c = 0xFF555555;
                }
                pixel(x, y, c);
            }
        }
        // "Inventory" caption and the player's slots, the same in every interface.
        caption(8, 73, new int[]{4, 4, 3, 5, 3, 4, 4, 3, 3});
        slots(7, 83, 9, 3);
        slots(7, 141, 9, 1);
        dust(8 + 18, 84 + 18);
        apple(8 + 18 * 7, 84);
        diamond(8 + 18 * 2, 142);
        coal(8 + 18 * 5, 142);
        switch (kind) {
            case CHEST -> {
                // Title as dark bars of letter-like blocks.
                caption(8, 6, new int[]{5, 3, 4, 4, 3});
                slots(7, 17, 9, 3);
                diamond(8 + 18, 18);
                ingot(8 + 18 * 3, 18 + 18, 0xFFFFE15A, 0xFFC99A1E);
                apple(8 + 18 * 6, 18);
                ingot(8 + 18 * 8, 18 + 36, 0xFFE6E6E6, 0xFF9C9C9C);
                emerald(8 + 18 * 4, 18 + 36);
            }
            case FURNACE -> {
                caption(70, 6, new int[]{4, 3, 4, 3, 3, 4, 3});
                slots(55, 16, 1, 1);
                slots(55, 52, 1, 1);
                bigSlot(111, 30);
                flame(56, 36);
                arrow(79, 34);
                ingot(56, 17, 0xFFD8D8D8, 0xFF8A8A8A);
                coal(56, 53);
                ingot(116, 35, 0xFFE6E6E6, 0xFF9C9C9C);
            }
            case INVENTORY -> {
                caption(97, 6, new int[]{4, 3, 4, 3, 3, 4, 3, 3});
                slots(7, 7, 1, 4);
                fill(25, 7, 51, 72, 0xFF000000);
                player(38, 12);
                slots(76, 61, 1, 1);
                slots(97, 17, 2, 2);
                arrow(120, 27);
                bigSlot(144, 25);
                ingot(8, 26, 0xFFE6E6E6, 0xFF9C9C9C);
                apple(98, 18);
                emerald(116, 36);
            }
            case CRAFTING -> {
                caption(29, 6, new int[]{4, 3, 4, 3, 3, 4, 3, 4});
                slots(29, 16, 3, 3);
                arrow(88, 26);
                bigSlot(119, 30);
                ingot(30, 17, 0xFFE6E6E6, 0xFF9C9C9C);
                ingot(48, 17, 0xFFE6E6E6, 0xFF9C9C9C);
                coal(48, 35);
                diamond(48, 53);
                diamond(124, 35);
            }
        }
    }

    /** A 26×26 result slot. */
    private void bigSlot(int x, int y) {
        fill(x, y, 26, 26, 0xFF8B8B8B);
        fill(x, y, 25, 1, 0xFF373737);
        fill(x, y, 1, 25, 0xFF373737);
        fill(x + 1, y + 25, 25, 1, 0xFFFFFFFF);
        fill(x + 25, y + 1, 1, 25, 0xFFFFFFFF);
    }

    /** The burning flame between the furnace's slots. */
    private void flame(int x, int y) {
        for (int dy = 0; dy < 14; dy++) {
            int half = Math.max(0, Math.min(6, (dy + 1) / 2));
            for (int dx = -half; dx <= half; dx++) {
                int c = dy > 9 ? 0xFFFFE15A : Math.abs(dx) < half - 1 ? 0xFFFFA020 : 0xFFE0451B;
                pixel(x + 7 + dx, y + dy, c);
            }
        }
    }

    /** The progress arrow (half filled). */
    private void arrow(int x, int y) {
        for (int dx = 0; dx < 22; dx++) {
            int half = dx < 15 ? 2 : Math.max(0, 7 - (dx - 15));
            for (int dy = -half; dy <= half; dy++) {
                pixel(x + dx, y + 8 + dy, dx < 11 ? 0xFFFFFFFF : 0xFF8B8B8B);
            }
        }
    }

    /** A blocky little player in the inventory's picture. */
    private void player(int x, int y) {
        fill(x + 8, y, 10, 10, 0xFFB98A6A);
        fill(x + 8, y, 10, 3, 0xFF4A2F1E);
        fill(x + 10, y + 5, 2, 1, 0xFF2E2E7A);
        fill(x + 14, y + 5, 2, 1, 0xFF2E2E7A);
        fill(x + 7, y + 10, 12, 16, 0xFF2FA7B8);
        fill(x + 3, y + 10, 4, 15, 0xFF2FA7B8);
        fill(x + 19, y + 10, 4, 15, 0xFF2FA7B8);
        fill(x + 3, y + 25, 4, 3, 0xFFB98A6A);
        fill(x + 19, y + 25, 4, 3, 0xFFB98A6A);
        fill(x + 7, y + 26, 12, 18, 0xFF3B3B9E);
        fill(x + 7, y + 44, 12, 4, 0xFF5A5A5A);
    }

    private static boolean corner(int x, int y) {
        int r = 3;
        int cx = x < r ? r : x >= WIDTH - r ? WIDTH - r - 1 : x;
        int cy = y < r ? r : y >= HEIGHT - r ? HEIGHT - r - 1 : y;
        if (cx == x || cy == y) {
            return x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT;
        }
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy > r * r;
    }

    private void caption(int x, int y, int[] letters) {
        int cx = x;
        for (int letter : letters) {
            fill(cx, y, letter, 7, 0xFF404040);
            fill(cx + 1, y + 2, Math.max(1, letter - 2), 3, 0xFFC6C6C6);
            cx += letter + 1;
        }
    }

    private void slots(int x, int y, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = x + c * 18;
                int sy = y + r * 18;
                fill(sx, sy, 18, 18, 0xFF8B8B8B);
                fill(sx, sy, 17, 1, 0xFF373737);
                fill(sx, sy, 1, 17, 0xFF373737);
                fill(sx + 1, sy + 17, 17, 1, 0xFFFFFFFF);
                fill(sx + 17, sy + 1, 1, 17, 0xFFFFFFFF);
            }
        }
    }

    private void diamond(int x, int y) {
        for (int dy = 0; dy < 12; dy++) {
            int half = dy < 4 ? 3 + dy : 9 - (dy - 4) * 9 / 8;
            for (int dx = -half; dx <= half; dx++) {
                int c = dy < 4 ? 0xFFB9FFF7 : (dx < 0 ? 0xFF4AEDD9 : 0xFF2CB8A8);
                pixel(x + 8 + dx, y + 2 + dy, c);
            }
        }
    }

    private void ingot(int x, int y, int light, int dark) {
        for (int dy = 0; dy < 6; dy++) {
            for (int dx = dy; dx < 14 - dy / 2; dx++) {
                pixel(x + 1 + dx, y + 6 + dy, dy < 2 ? light : dy == 5 ? dark : lerp(light, dark, dy / 6f));
            }
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void apple(int x, int y) {
        for (int dy = 0; dy < 11; dy++) {
            for (int dx = 0; dx < 12; dx++) {
                double ex = (dx - 5.5) / 6.0;
                double ey = (dy - 5.5) / 5.6;
                if (ex * ex + ey * ey <= 1) {
                    pixel(x + 2 + dx, y + 4 + dy, dx < 4 && dy < 4 ? 0xFFFF8A8A : 0xFFD8262B);
                }
            }
        }
        fill(x + 8, y + 1, 1, 4, 0xFF5B3A1E);
        fill(x + 9, y + 2, 3, 2, 0xFF3FA34A);
    }

    private void emerald(int x, int y) {
        for (int dy = 0; dy < 12; dy++) {
            int half = dy < 6 ? 1 + dy / 2 + 1 : 1 + (11 - dy) / 2 + 1;
            for (int dx = -half; dx <= half; dx++) {
                pixel(x + 8 + dx, y + 2 + dy, dx < 0 ? 0xFF41F384 : 0xFF17A34A);
            }
        }
    }

    private void dust(int x, int y) {
        int[][] dots = {{4, 9}, {6, 7}, {8, 10}, {10, 6}, {11, 9}, {7, 12}, {5, 11}, {9, 8}};
        for (int[] d : dots) {
            fill(x + d[0], y + d[1], 2, 2, 0xFFE0221B);
        }
    }

    private void coal(int x, int y) {
        for (int dy = 0; dy < 9; dy++) {
            for (int dx = 0; dx < 10; dx++) {
                double ex = (dx - 4.5) / 5.0;
                double ey = (dy - 4.0) / 4.5;
                if (ex * ex + ey * ey <= 1) {
                    pixel(x + 3 + dx, y + 5 + dy, (dx + dy) % 4 == 0 ? 0xFF5A5A5A : 0xFF262626);
                }
            }
        }
    }
}
