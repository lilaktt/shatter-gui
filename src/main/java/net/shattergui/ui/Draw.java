package net.shattergui.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.shattergui.mixin.GuiGraphicsExtractorAccessor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Drawing primitives of the editor, all built from {@code fill}/{@code fillGradient} and the pose stack: rounded
 * rectangles (corners as short scanlines), soft shadows, outlines, pixel icons; plus textured or coloured triangles as
 * a GUI render element (the effect previews).
 */
public final class Draw {
    private Draw() {
    }

    public static void rect(Ui ui, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0 && (color >>> 24) != 0) {
            ui.g.fill(x, y, x + w, y + h, color);
        }
    }

    /** Corner inset of row {@code i} (0 = outermost) of a quarter circle of radius r. */
    private static int inset(int r, int i) {
        double d = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
    }

    public static void roundRect(Ui ui, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ui.g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            ui.g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            ui.g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
        }
        ui.g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Vertical gradient from {@code top} to {@code bottom}. */
    public static void roundGradient(Ui ui, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            ui.g.fill(x + in, y + i, x + w - in, y + i + 1, Theme.mix(top, bottom, (i + 0.5f) / h));
            ui.g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, Theme.mix(top, bottom, (h - i - 0.5f) / h));
        }
        ui.g.fillGradient(x, y + r, x + w, y + h - r, Theme.mix(top, bottom, (float) r / h),
                Theme.mix(top, bottom, (float) (h - r) / h));
    }

    /** A 1-pixel rounded border. */
    public static void roundOutline(Ui ui, int x, int y, int w, int h, int r, int color) {
        if (w <= 2 || h <= 2 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ui.g.outline(x, y, w, h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            int next = i + 1 < r ? inset(r, i + 1) : 0;
            int span = Math.max(1, in - next);
            // Top and bottom arcs: the pixels of this row that are not covered by the next row.
            ui.g.fill(x + in, y + i, x + in + span, y + i + 1, color);
            ui.g.fill(x + w - in - span, y + i, x + w - in, y + i + 1, color);
            ui.g.fill(x + in, y + h - i - 1, x + in + span, y + h - i, color);
            ui.g.fill(x + w - in - span, y + h - i - 1, x + w - in, y + h - i, color);
        }
        ui.g.fill(x + r, y, x + w - r, y + 1, color);
        ui.g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        ui.g.fill(x, y + r, x + 1, y + h - r, color);
        ui.g.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    /** A rounded rectangle with a 1-pixel border of another colour (border shape, then the fill inset by one). */
    public static void roundBox(Ui ui, int x, int y, int w, int h, int r, int fill, int border) {
        if ((border >>> 24) == 0) {
            roundRect(ui, x, y, w, h, r, fill);
            return;
        }
        roundRect(ui, x, y, w, h, r, border);
        roundRect(ui, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** A soft drop shadow under a rounded rectangle; {@code strength} 0..1. */
    public static void shadow(Ui ui, int x, int y, int w, int h, int r, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        int[] alphas = {40, 16};
        for (int i = 0; i < alphas.length; i++) {
            int a = Math.round(alphas[i] * strength);
            int grow = i + 1;
            roundRect(ui, x - grow, y - grow + 2 + i * 2, w + grow * 2, h + grow * 2, r + grow, a << 24);
        }
    }

    /** A pixel icon, {@code scale} screen pixels per icon pixel. */
    public static void icon(Ui ui, Icon icon, int x, int y, int scale, int color) {
        for (int py = 0; py < Icon.SIZE; py++) {
            int run = -1;
            for (int px = 0; px <= Icon.SIZE; px++) {
                boolean on = px < Icon.SIZE && icon.at(px, py);
                if (on && run < 0) {
                    run = px;
                } else if (!on && run >= 0) {
                    ui.g.fill(x + run * scale, y + py * scale, x + px * scale, y + (py + 1) * scale, color);
                    run = -1;
                }
            }
        }
    }

    /** An icon centred in a box. */
    public static void iconCentered(Ui ui, Icon icon, int x, int y, int w, int h, int color) {
        int size = Icon.SIZE;
        icon(ui, icon, x + (w - size) / 2, y + (h - size) / 2, 1, color);
    }

    /** A filled circle (for swatches and knobs). */
    public static void circle(Ui ui, int cx, int cy, int radius, int color) {
        roundRect(ui, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** A whole texture stretched over a rectangle (two triangles). */
    public static void texture(Ui ui, AbstractTexture texture, float x, float y, float w, float h, int color) {
        float x1 = x + w;
        float y1 = y + h;
        texture(ui, texture, x, y, w, h, color, 0, 0, 1, 1, false);
    }

    /**
     * As above, the part (u0, v0)–(u1, v1) of the texture (v0 > v1 flips it); {@code premultiplied}: see
     * {@link #triangles}.
     */
    public static void texture(Ui ui, AbstractTexture texture, float x, float y, float w, float h, int color,
                               float u0, float v0, float u1, float v1, boolean premultiplied) {
        float x1 = x + w;
        float y1 = y + h;
        float[] data = {x, y, u0, v0, x, y1, u0, v1, x1, y1, u1, v1, x, y, u0, v0, x1, y1, u1, v1, x1, y, u1, v0};
        triangles(ui, texture, premultiplied, data, new int[]{color, color, color, color, color, color}, 6);
    }

    // --- triangles ---

    /**
     * A batch of triangles: {@code data} holds per vertex {x, y, u, v} in GUI coordinates (u, v ignored without a
     * texture) and {@code colors} one ARGB per vertex. Drawn with the current pose and clip. Triangles of either
     * winding are fine (the GUI pipelines cull back faces, so they are reordered here).
     */
    public static void triangles(Ui ui, AbstractTexture texture, float[] data, int[] colors, int vertexCount) {
        triangles(ui, texture, false, data, colors, vertexCount);
    }

    /**
     * As above; {@code premultiplied}: the texture's colours are already multiplied by its alpha (a GUI captured on a
     * transparent target), so it is blended that way and the vertex colours are premultiplied to match.
     */
    public static void triangles(Ui ui, AbstractTexture texture, boolean premultiplied, float[] data, int[] colors,
                                 int vertexCount) {
        if (vertexCount < 3) {
            return;
        }
        if (premultiplied && texture != null) {
            colors = colors.clone();
            for (int i = 0; i < vertexCount; i++) {
                colors[i] = premultiply(colors[i]);
            }
        }
        Matrix3x2f pose = new Matrix3x2f(ui.g.pose());
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            minX = Math.min(minX, data[i * 4]);
            maxX = Math.max(maxX, data[i * 4]);
            minY = Math.min(minY, data[i * 4 + 1]);
            maxY = Math.max(maxY, data[i * 4 + 1]);
        }
        ScreenRectangle clip = ui.clip();
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1).transformMaxBounds(pose);
        if (clip != null) {
            ScreenRectangle cut = clip.intersection(bounds);
            if (cut == null) {
                return;
            }
            bounds = cut;
        }
        TextureSetup setup = texture != null ? TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler())
                : TextureSetup.noTexture();
        ((GuiGraphicsExtractorAccessor) ui.g).shatter$renderState().addGuiElement(new Triangles(
                texture == null ? RenderPipelines.GUI : premultiplied ? RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
                        : RenderPipelines.GUI_TEXTURED, setup, pose,
                data.clone(), colors.clone(), vertexCount - vertexCount % 3, clip, bounds));
    }

    private static int premultiply(int argb) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 0xFF) * a / 255;
        int g = ((argb >> 8) & 0xFF) * a / 255;
        int b = (argb & 0xFF) * a / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private record Triangles(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, TextureSetup textureSetup,
                             Matrix3x2fc pose, float[] data, int[] colors, int count, ScreenRectangle scissorArea,
                             ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            boolean textured = pipeline != RenderPipelines.GUI;
            for (int t = 0; t < count; t += 3) {
                int a = t;
                int b = t + 1;
                int c = t + 2;
                // Same winding as vanilla quads (top-left, bottom-left, bottom-right): negative cross product.
                float cross = (data[b * 4] - data[a * 4]) * (data[c * 4 + 1] - data[a * 4 + 1])
                        - (data[b * 4 + 1] - data[a * 4 + 1]) * (data[c * 4] - data[a * 4]);
                if (cross > 0) {
                    int swap = b;
                    b = c;
                    c = swap;
                }
                // A quad with the last vertex doubled draws exactly the triangle.
                vertex(consumer, a, textured);
                vertex(consumer, b, textured);
                vertex(consumer, c, textured);
                vertex(consumer, c, textured);
            }
        }

        private void vertex(VertexConsumer consumer, int i, boolean textured) {
            VertexConsumer v = consumer.addVertexWith2DPose(pose, data[i * 4], data[i * 4 + 1]);
            if (textured) {
                v.setUv(data[i * 4 + 2], data[i * 4 + 3]);
            }
            v.setColor(colors[i]);
        }
    }
}
