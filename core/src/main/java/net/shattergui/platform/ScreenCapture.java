package net.shattergui.platform;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.params.ParamValues;
import net.shattergui.ui.preview.SampleImage;

/**
 * Pictures of screens: the frame of a closed screen that the shards are cut from (taken by the platform when a screen
 * closes), the real vanilla screens drawn into textures for the previews, and the editor playing an effect in the
 * world on a picture of itself.
 */
public interface ScreenCapture {
    /** Drops a capture that waits for its frame (the master switch went off). */
    void cancelPending();

    // --- the real preview (vanilla screens drawn into textures) ---

    /** The real vanilla screen behind a sample, or null while it is not drawn (or the real preview is off). */
    SampleImage.Image realPreview(SampleImage.Kind sample);

    /** Real pictures are wanted (setting or quick switch) and nothing has failed this session. */
    boolean realPreviewEnabled();

    /** The settings screen's value (saved or not); the quick switch then follows it again. */
    void setRealPreviewConfigured(boolean on);

    /** The preview pane's switch: real or simple pictures for this session. */
    void toggleRealPreview();

    /** A world is loaded (the real screens need the player). */
    boolean realPreviewAvailable();

    /** Switched to simple pictures by an error a moment ago: the editor shows a small notice. */
    boolean realPreviewNotice();

    /** Frees the real preview's textures (the settings closed). */
    void releaseRealPreview();

    // --- playing in the world from the editor ---

    /**
     * Closes {@code screen} with the effect {@code id} (a custom one: {@code custom}, its base and its settings) and
     * opens it again when the effect is over. False without a world to play it in.
     */
    boolean playInWorld(Object screen, String id, CustomEffectDef custom, ParamValues params);

    /** As above with a schema-2 effect built just now ({@link EffectCatalog#build}). */
    boolean playInWorld(Object screen, EffectCatalog.Built effect, ParamValues params);

    /** An effect started by {@link #playInWorld} is still playing. */
    boolean playingInWorld();
}
