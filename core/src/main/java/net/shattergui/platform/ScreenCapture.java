package net.shattergui.platform;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.params.ParamValues;
import net.shattergui.ui.preview.SampleImage;

public interface ScreenCapture {
    void cancelPending();

    SampleImage.Image realPreview(SampleImage.Kind sample);

    boolean realPreviewEnabled();

    void setRealPreviewConfigured(boolean on);

    void toggleRealPreview();

    boolean realPreviewAvailable();

    boolean realPreviewNotice();

    void releaseRealPreview();

    boolean playInWorld(Object screen, String id, CustomEffectDef custom, ParamValues params);

    boolean playInWorld(Object screen, EffectCatalog.Built effect, ParamValues params);

    boolean playingInWorld();
}
