package net.shattergui.platform;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.game.FxTextures;
import net.shattergui.params.ParamValues;

import java.util.Collection;
import java.util.List;

public interface EffectCatalog {
    interface Data {
        FxDef def();

        FxTextures textures();

        boolean bundled();

        String origin();

        List<String> loadedTextures();
    }

    interface Built {
    }

    record Opening(Data data, String reversedBase, ParamValues prepared) {
    }

    List<String> ids();

    List<String> builtInIds();

    List<String> dataIds();

    List<String> openIds();

    String resolve(String id);

    boolean isBuiltIn(String id);

    boolean isBundled(String id);

    boolean isReserved(String id);

    boolean canOpen(String id);

    boolean isFlyBased(String id);

    Text displayName(String id);

    Data data(String id);

    Opening opening(String id, ParamValues params);

    String bundledJson(String id);

    FxProblems importJson(String json);

    String toCode(Data effect);

    List<String> messages();

    void reloadAll();

    Built build(FxDef def, List<String> warnings);

    void release(Built effect);

    Collection<CustomEffectDef> customFromConfig();

    Collection<CustomEffectDef> customFromPacks();

    void reloadCustom();

    void saveCustom(Collection<CustomEffectDef> defs);
}
