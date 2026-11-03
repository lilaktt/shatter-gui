package net.shattergui.platform.impl;

import net.minecraft.client.gui.screens.Screen;
import net.shattergui.config.screen.PreviewRunner;
import net.shattergui.custom.CustomEffect;
import net.shattergui.custom.CustomEffectDef;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.params.ParamValues;
import net.shattergui.platform.EffectCatalog;
import net.shattergui.platform.ScreenCapture;
import net.shattergui.ui.preview.RealPreview;
import net.shattergui.ui.preview.SampleImage;

/** Screen pictures on 26.1: the capture of closed screens, the real preview, the editor's in-world preview. */
final class Capture26 implements ScreenCapture {
    @Override
    public void cancelPending() {
        net.shattergui.capture.ScreenCapture.cancelPending();
    }

    @Override
    public SampleImage.Image realPreview(SampleImage.Kind sample) {
        return RealPreview.image(sample);
    }

    @Override
    public boolean realPreviewEnabled() {
        return RealPreview.enabled();
    }

    @Override
    public void setRealPreviewConfigured(boolean on) {
        RealPreview.setConfigured(on);
    }

    @Override
    public void toggleRealPreview() {
        RealPreview.toggleQuick();
    }

    @Override
    public boolean realPreviewAvailable() {
        return RealPreview.available();
    }

    @Override
    public boolean realPreviewNotice() {
        return RealPreview.notice();
    }

    @Override
    public void releaseRealPreview() {
        RealPreview.release();
    }

    @Override
    public boolean playInWorld(Object screen, String id, CustomEffectDef custom, ParamValues params) {
        // A custom effect as edited (unsaved), else the registered one.
        ShatterEffect effect = custom != null ? new CustomEffect(custom, ShatterEffects.builtIn(custom.base()))
                : ShatterEffects.resolve(id);
        return PreviewRunner.start((Screen) screen, effect, params);
    }

    @Override
    public boolean playInWorld(Object screen, EffectCatalog.Built effect, ParamValues params) {
        return PreviewRunner.start((Screen) screen, (DataEffect) effect, params);
    }

    @Override
    public boolean playingInWorld() {
        return PreviewRunner.running();
    }
}
