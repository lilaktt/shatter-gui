package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxParser;
import net.shattergui.ui.Anim;
import net.shattergui.ui.Button;
import net.shattergui.ui.Card;
import net.shattergui.ui.Column;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Grid;
import net.shattergui.ui.Label;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiClock;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

/**
 * "New effect": the starting points as cards, each playing its effect in a little live preview — empty, glass, burn,
 * dissolve, vortex, particle rain. A click starts a new, unsaved effect from it.
 */
final class TemplateGallery extends UiScreen.Overlay {
    private final Column content = new Column(0, 6);
    private final ScrollView scroll = new ScrollView(content);
    private final Button cancel;
    private final Anim appear = new Anim(0f, Theme.SPEED_APPEAR + 3f);

    private TemplateGallery(FxEditor editor) {
        content.add(new Label(EditorUi.tr("gallery.desc"), Label.Style.DIM));
        Grid grid = content.add(new Grid(112, 6));
        for (String id : Templates.IDS) {
            Card card = new Card(5, 3).onClick(() -> {
                UiScreen.active().closeOverlay(this);
                editor.newEffect(id);
            });
            card.add(new Mini(editor, id));
            card.add(new Label(EditorUi.tr("template." + id), Label.Style.BODY));
            card.add(new Label(EditorUi.tr("template." + id + ".desc"), Label.Style.DIM));
            card.tooltip(EditorUi.tr("template." + id), EditorUi.tr("template." + id + ".desc"));
            grid.add(card);
        }
        add(scroll);
        cancel = add(new Button(Component.translatable("gui.cancel"), null, Button.Kind.SECONDARY,
                () -> UiScreen.active().closeOverlay(this)));
    }

    static void open(FxEditor editor) {
        UiScreen.active().openOverlay(new TemplateGallery(editor));
    }

    /** A template playing in a loop. */
    private static final class Mini extends Widget {
        private final EditorPreview preview;
        private long frame = -1;

        Mini(FxEditor editor, String id) {
            this.preview = new EditorPreview(editor.values, name -> null);
            FxParser.Result result = FxParser.parse(Templates.json(id));
            preview.setDef(result.def());
            preview.setLoop(true);
            this.h = 58;
        }

        @Override
        protected int measure(int width) {
            return Math.round(Math.min(70, width * 0.62f));
        }

        @Override
        protected void draw(Ui ui) {
            if (frame != UiClock.frameNumber()) {
                frame = UiClock.frameNumber();
                preview.frame(UiClock.dt());
            }
            preview.render(ui, x, y, w, h, false);
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
        w = Math.min(400, screenWidth - 16);
        h = Math.min(320, screenHeight - 16);
        x = (screenWidth - w) / 2;
        y = (screenHeight - h) / 2;
        scroll.setBounds(x + 8, y + 22, w - 12, h - 22 - 26);
        int bw = Math.max(70, cancel.naturalWidth());
        cancel.setBounds(x + w - 8 - bw, y + h - 22, bw, 18);
    }

    @Override
    protected void draw(Ui ui) {
        float a = appear.target(1f).tick();
        ui.g.pose().pushMatrix();
        ui.g.pose().translate(0, (1 - a) * 6);
        Draw.shadow(ui, x, y, w, h, Theme.RADIUS_CARD, 1f);
        Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CARD, 0xFF17181F, Theme.BORDER_STRONG);
        Draw.roundRect(ui, x, y, w, 3, 1, Theme.accent());
        ui.text(EditorUi.tr("gallery.title"), x + 10, y + 9, Theme.TEXT);
        super.draw(ui);
        ui.g.pose().popMatrix();
    }
}
