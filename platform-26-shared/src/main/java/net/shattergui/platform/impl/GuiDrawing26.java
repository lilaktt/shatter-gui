package net.shattergui.platform.impl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.shattergui.mixin.GuiGraphicsExtractorAccessor;
import net.shattergui.platform.GuiDrawing;
import net.shattergui.platform.Text;
import net.shattergui.platform.TextLine;
import net.shattergui.platform.UiFont;
import net.shattergui.platform.UiItem;
import net.shattergui.platform.UiPose;
import net.shattergui.platform.UiRect;
import net.shattergui.platform.UiTexture;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Core's drawing on 26.1's {@link GuiGraphicsExtractor}: fills, text and items as the game's own GUI elements, the
 * editor's textured or coloured triangles as a GUI render element of their own (the effect previews).
 */
public final class GuiDrawing26 implements GuiDrawing, UiPose {
    private final GuiGraphicsExtractor g;

    public GuiDrawing26(GuiGraphicsExtractor g) {
        this.g = g;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    @Override
    public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        g.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    @Override
    public void outline(int x, int y, int w, int h, int color) {
        g.outline(x, y, w, h, color);
    }

    @Override
    public void text(UiFont font, Text text, int x, int y, int color, boolean shadow) {
        g.text(Minecraft.getInstance().font, ComponentText.unwrap(text), x, y, color, shadow);
    }

    @Override
    public void text(UiFont font, String text, int x, int y, int color, boolean shadow) {
        g.text(Minecraft.getInstance().font, text, x, y, color, shadow);
    }

    @Override
    public void text(UiFont font, TextLine line, int x, int y, int color, boolean shadow) {
        g.text(Minecraft.getInstance().font, ((Texts26.Line) line).sequence(), x, y, color, shadow);
    }

    @Override
    public void item(UiItem item, int x, int y) {
        g.item(new ItemStack(switch (item) {
            case CHEST -> Items.CHEST;
            case FURNACE -> Items.FURNACE;
            case CRAFTING_TABLE -> Items.CRAFTING_TABLE;
        }), x, y);
    }

    /** Any item of the game (the screen icons of the settings). */
    public void item(ItemStack stack, int x, int y) {
        g.item(stack, x, y);
    }

    @Override
    public UiPose pose() {
        return this;
    }

    @Override
    public void enableScissor(int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    @Override
    public void disableScissor() {
        g.disableScissor();
    }

    @Override
    public void nextStratum() {
        g.nextStratum();
    }

    // --- the pose ---

    @Override
    public void pushMatrix() {
        g.pose().pushMatrix();
    }

    @Override
    public void popMatrix() {
        g.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        g.pose().scale(x, y);
    }

    @Override
    public void rotate(float angle) {
        g.pose().rotate(angle);
    }

    @Override
    public Object snapshot() {
        return new Matrix3x2f(g.pose());
    }

    @Override
    public void restore(Object snapshot) {
        g.pose().set((Matrix3x2fc) snapshot);
    }

    // --- triangles ---

    @Override
    public void triangles(UiTexture texture, boolean premultiplied, float[] data, int[] colors, int vertexCount,
                          UiRect uiClip) {
        AbstractTexture gpu = texture == null ? null : ((Texture26) texture).texture();
        Matrix3x2f pose = new Matrix3x2f(g.pose());
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
        ScreenRectangle clip = uiClip == null ? null
                : new ScreenRectangle(uiClip.left(), uiClip.top(), uiClip.width(), uiClip.height());
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1).transformMaxBounds(pose);
        if (clip != null) {
            ScreenRectangle cut = clip.intersection(bounds);
            if (cut == null) {
                return;
            }
            bounds = cut;
        }
        TextureSetup setup = gpu != null ? TextureSetup.singleTexture(gpu.getTextureView(), gpu.getSampler())
                : TextureSetup.noTexture();
        ((GuiGraphicsExtractorAccessor) g).shatter$renderState().addGuiElement(new Triangles(
                gpu == null ? RenderPipelines.GUI : premultiplied ? RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
                        : RenderPipelines.GUI_TEXTURED, setup, pose,
                data, colors, vertexCount - vertexCount % 3, clip, bounds));
    }

    private record Triangles(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float[] data,
                             int[] colors, int count, ScreenRectangle scissorArea,
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
