package net.shattergui.platform.impl;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.shattergui.platform.GuiDrawing;
import net.shattergui.platform.Text;
import net.shattergui.platform.TextLine;
import net.shattergui.platform.UiFont;
import net.shattergui.platform.UiItem;
import net.shattergui.platform.UiPose;
import net.shattergui.platform.UiRect;
import net.shattergui.platform.UiTexture;
import org.joml.Matrix4f;

/**
 * Core's drawing on 1.21.1's {@link GuiGraphics}, which draws at once in call order: a new stratum is a flush, the pose
 * is a 4×4 {@code PoseStack}. The textured triangles of the previews are not ported yet.
 */
public final class GuiDrawing121 implements GuiDrawing, UiPose {
    private final GuiGraphics g;

    public GuiDrawing121(GuiGraphics g) {
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
        g.renderOutline(x, y, w, h, color);
    }

    @Override
    public void text(UiFont font, Text text, int x, int y, int color, boolean shadow) {
        g.drawString(Minecraft.getInstance().font, ComponentText.unwrap(text), x, y, color, shadow);
    }

    @Override
    public void text(UiFont font, String text, int x, int y, int color, boolean shadow) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, shadow);
    }

    @Override
    public void text(UiFont font, TextLine line, int x, int y, int color, boolean shadow) {
        g.drawString(Minecraft.getInstance().font, ((Texts121.Line) line).sequence(), x, y, color, shadow);
    }

    @Override
    public void item(UiItem item, int x, int y) {
        g.renderItem(new ItemStack(switch (item) {
            case CHEST -> Items.CHEST;
            case FURNACE -> Items.FURNACE;
            case CRAFTING_TABLE -> Items.CRAFTING_TABLE;
        }), x, y);
    }

    /** A game item (the settings' screen icons). */
    public void item(ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
    }

    /** The game's drawing object of this frame. */
    public GuiGraphics graphics() {
        return g;
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
        g.flush();
    }

    @Override
    public void pushMatrix() {
        g.pose().pushPose();
    }

    @Override
    public void popMatrix() {
        g.pose().popPose();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y, 0f);
    }

    @Override
    public void scale(float x, float y) {
        g.pose().scale(x, y, 1f);
    }

    @Override
    public void rotate(float angle) {
        g.pose().mulPose(Axis.ZP.rotation(angle));
    }

    @Override
    public Object snapshot() {
        return new Matrix4f(g.pose().last().pose());
    }

    @Override
    public void restore(Object snapshot) {
        g.pose().last().pose().set((Matrix4f) snapshot);
    }

    @Override
    public void triangles(UiTexture texture, boolean premultiplied, float[] data, int[] colors, int vertexCount,
                          UiRect clip) {
        throw new UnsupportedOperationException("Not ported to Minecraft 1.21.1 yet: textured GUI triangles (the effect previews)");
    }
}
