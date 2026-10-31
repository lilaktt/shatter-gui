package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxLimits;
import net.shattergui.ui.Anim;
import net.shattergui.ui.Button;
import net.shattergui.ui.Card;
import net.shattergui.ui.Column;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Grid;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Label;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

/**
 * "Add": the catalogue of what can be added — every module with its icon, name and one sentence on what it does,
 * then particles (vanilla or an own sprite) and a sound. A click adds it with sensible defaults and selects it.
 */
final class ModuleCatalog extends UiScreen.Overlay {
    private final Column content = new Column(0, 8);
    private final ScrollView scroll = new ScrollView(content);
    private final Button close;
    private final Anim appear = new Anim(0f, Theme.SPEED_APPEAR + 3f);

    private ModuleCatalog(FxEditor editor) {
        content.add(new Label(EditorUi.tr("catalog.modules"), Label.Style.HEADING));
        Grid modules = content.add(new Grid(118, 6));
        boolean layersFull = editor.doc.size("layers") >= FxLimits.MAX_LAYERS;
        for (FxDef.Module module : FxDef.Module.values()) {
            modules.add(card(EditorUi.icon(module), EditorUi.color(module), EditorUi.moduleName(module),
                    EditorUi.moduleDescription(module), !layersFull, () -> editor.addLayer(module)));
        }
        content.add(new Label(EditorUi.tr("catalog.more"), Label.Style.HEADING));
        Grid more = content.add(new Grid(118, 6));
        boolean emittersFull = editor.doc.size("emitters") >= FxLimits.MAX_EMITTERS;
        more.add(card(Icon.SPARKLES, EditorUi.EMITTER_COLOR, EditorUi.tr("catalog.particles"), EditorUi.tr("catalog.particles.desc"),
                !emittersFull, () -> editor.addEmitter(false)));
        more.add(card(Icon.IMAGE, EditorUi.EMITTER_COLOR, EditorUi.tr("catalog.sprites"), EditorUi.tr("catalog.sprites.desc"),
                !emittersFull, () -> editor.addEmitter(true)));
        more.add(card(Icon.NOTE, EditorUi.SOUND_COLOR, EditorUi.tr("catalog.sound"), EditorUi.tr("catalog.sound.desc"),
                editor.doc.size("sounds") < FxLimits.MAX_SOUNDS, editor::addSound));
        add(scroll);
        close = add(new Button(Component.translatable("gui.cancel"), null, Button.Kind.SECONDARY,
                () -> UiScreen.active().closeOverlay(this)));
    }

    static void open(FxEditor editor) {
        UiScreen.active().openOverlay(new ModuleCatalog(editor));
    }

    private Card card(Icon icon, int color, Component name, Component description, boolean available, Runnable action) {
        Card card = new Card(6, 3);
        if (available) {
            card.onClick(() -> {
                UiScreen.active().closeOverlay(this);
                action.run();
            });
        }
        card.add(new CardHead(icon, color, name, available));
        card.add(new Label(description, Label.Style.DIM));
        card.tooltip(name, available ? description : EditorUi.tr("catalog.full"));
        return card;
    }

    /** Icon in a coloured tile, and the name. */
    private static final class CardHead extends Widget {
        private final Icon icon;
        private final int color;
        private final Component name;
        private final boolean available;

        CardHead(Icon icon, int color, Component name, boolean available) {
            this.icon = icon;
            this.color = color;
            this.name = name;
            this.available = available;
            this.h = 16;
        }

        @Override
        protected void draw(Ui ui) {
            int c = available ? color : Theme.TEXT_FAINT;
            Draw.roundRect(ui, x, y, 16, 16, 4, Theme.fade(c, 0.25f));
            Draw.icon(ui, icon, x + 4, y + 4, 1, Theme.mix(c, 0xFFFFFFFF, 0.25f));
            ui.textFit(name, x + 21, y + 4, w - 21, available ? Theme.TEXT : Theme.TEXT_FAINT);
        }

        @Override
        public Widget hit(double mx, double my) {
            return null;
        }
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public void outsideClick() {
        UiScreen.active().closeOverlay(this);
    }

    @Override
    public void place(int screenWidth, int screenHeight) {
        w = Math.min(420, screenWidth - 16);
        h = Math.min(300, screenHeight - 16);
        x = (screenWidth - w) / 2;
        y = (screenHeight - h) / 2;
        scroll.setBounds(x + 8, y + 22, w - 12, h - 22 - 26);
        int bw = Math.max(70, close.naturalWidth());
        close.setBounds(x + w - 8 - bw, y + h - 22, bw, 18);
    }

    @Override
    protected void draw(Ui ui) {
        float a = appear.target(1f).tick();
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, (1 - a) * 6);
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CARD, 1f);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, 0xFF17181F, Theme.BORDER_STRONG);
        Draw.roundRect(ui, x, y, w, 3, 1, Theme.accent());
        ui.text(EditorUi.tr("catalog.title"), x + 10, y + 9, Theme.TEXT);
        super.draw(ui);
        ui.g.pose().popMatrix();
    }
}
