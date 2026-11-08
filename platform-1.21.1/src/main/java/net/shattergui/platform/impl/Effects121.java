package net.shattergui.platform.impl;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.custom.CustomEffects;
import net.shattergui.effect.FlyEffect;
import net.shattergui.effect.ReversedEffect;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.params.ParamValues;
import net.shattergui.platform.EffectCatalog;
import net.shattergui.platform.Text;

import java.util.Collection;
import java.util.List;

final class Effects121 implements EffectCatalog {
    @Override
    public List<String> ids() {
        return ShatterEffects.ids();
    }

    @Override
    public List<String> builtInIds() {
        return ShatterEffects.builtInIds();
    }

    @Override
    public List<String> dataIds() {
        return ShatterEffects.dataIds();
    }

    @Override
    public List<String> openIds() {
        return ShatterEffects.openIds();
    }

    @Override
    public String resolve(String id) {
        return ShatterEffects.resolve(id).id();
    }

    @Override
    public boolean isBuiltIn(String id) {
        return ShatterEffects.isBuiltIn(id);
    }

    @Override
    public boolean isBundled(String id) {
        return ShatterEffects.isBundled(id);
    }

    @Override
    public boolean isReserved(String id) {
        return ShatterEffects.isReserved(id);
    }

    @Override
    public boolean canOpen(String id) {
        return ShatterEffects.canOpen(id);
    }

    @Override
    public boolean isFlyBased(String id) {
        ShatterEffect effect = ShatterEffects.byName(id);
        return effect != null && effect.base() instanceof FlyEffect;
    }

    @Override
    public Text displayName(String id) {
        return ComponentText.of(ShatterEffects.displayName(id));
    }

    @Override
    public Data data(String id) {
        return ShatterEffects.data(id) instanceof DataEffect data ? data : null;
    }

    @Override
    public Opening opening(String id, ParamValues params) {
        ShatterEffect fx = ShatterEffects.opening(id);
        if (fx instanceof DataEffect data) {
            return new Opening(data, null, null);
        }
        if (fx instanceof ReversedEffect reversed) {
            return new Opening(null, reversed.base().id(), reversed.prepare(params));
        }
        return null;
    }

    @Override
    public String bundledJson(String id) {
        return FxEffects.bundledJson(id);
    }

    @Override
    public FxProblems importJson(String json) {
        return FxEffects.importJson(json);
    }

    @Override
    public String toCode(Data effect) {
        return FxEffects.toCode((DataEffect) effect);
    }

    @Override
    public List<String> messages() {
        return FxEffects.messages();
    }

    @Override
    public void reloadAll() {
        FxEffects.reloadAll();
    }

    @Override
    public Built build(FxDef def, List<String> warnings) {
        return FxEffects.build(def, warnings);
    }

    @Override
    public void release(Built effect) {
        FxEffects.release((DataEffect) effect);
    }

    @Override
    public Collection<CustomEffectDef> customFromConfig() {
        return CustomEffects.fromConfig();
    }

    @Override
    public Collection<CustomEffectDef> customFromPacks() {
        return CustomEffects.fromPacks();
    }

    @Override
    public void reloadCustom() {
        CustomEffects.reloadConfig();
    }

    @Override
    public void saveCustom(Collection<CustomEffectDef> defs) {
        CustomEffects.saveAll(defs);
    }
}
