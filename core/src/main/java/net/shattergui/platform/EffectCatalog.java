package net.shattergui.platform;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.game.FxTextures;
import net.shattergui.params.ParamValues;

import java.util.Collection;
import java.util.List;

/**
 * The loaded effects as the settings, the editor and the previews see them. The effects themselves (shards, waves,
 * particles in the world) live in the platform; this is their registry: the built-in effects, the custom (schema 1)
 * effects on a built-in base and the schema-2 data effects, in the order every list shows them.
 */
public interface EffectCatalog {
    /** A loaded schema-2 effect: its definition and textures, and whether it ships with the mod. */
    interface Data {
        FxDef def();

        FxTextures textures();

        /** Shipped with the mod: translated name, its own settings, not editable in place. */
        boolean bundled();

        /** Where it was loaded from (file, package or resource pack), for messages. */
        String origin();

        /** Names of the textures that loaded. */
        List<String> loadedTextures();
    }

    /** A schema-2 effect built just now for the editor's test: not registered, free it with {@link #release}. */
    interface Built {
    }

    /**
     * What a preview plays when a screen opens with an effect: a schema-2 effect backwards ({@code data}), or the
     * built-in base {@code reversedBase} played backwards with its own settings ({@code prepared}).
     */
    record Opening(Data data, String reversedBase, ParamValues prepared) {
    }

    /** Built-in ids, then custom ids, then schema-2 ids. */
    List<String> ids();

    List<String> builtInIds();

    List<String> dataIds();

    /** Ids that can open screens, in list order. */
    List<String> openIds();

    /** The id of the effect that plays for {@code id} (glass for an unknown one). */
    String resolve(String id);

    boolean isBuiltIn(String id);

    /** One of the effects shipped with the mod as data files. */
    boolean isBundled(String id);

    /** Ids that a player's effect may not take: the built-in and the shipped ones. */
    boolean isReserved(String id);

    boolean canOpen(String id);

    /** The named effect is fly or built on it (an unknown name is logged once, as for the config). */
    boolean isFlyBased(String id);

    /** Name for the GUI: translated for the built-in and shipped effects, the player's name for their own. */
    Text displayName(String id);

    /** A loaded schema-2 effect, or null. */
    Data data(String id);

    /** What opening with {@code id} plays (see {@link Opening}), or null if it has no opening. */
    Opening opening(String id, ParamValues params);

    /** The JSON of a shipped effect as in the jar, or null. */
    String bundledJson(String id);

    /** Saves an imported schema-2 effect (no textures) and reloads; the problems (nothing written on errors). */
    FxProblems importJson(String json);

    /** A share code of a loaded schema-2 effect without textures, or null. */
    String toCode(Data effect);

    /** What the last load of the schema-2 effects reported, one line each. */
    List<String> messages();

    /** Reads the schema-2 effects again. */
    void reloadAll();

    /** Builds an effect from a definition right now (the editor's "Test"), textures from the config and packs. */
    Built build(FxDef def, List<String> warnings);

    void release(Built effect);

    /** Custom (schema 1) effects of the config folder. */
    Collection<CustomEffectDef> customFromConfig();

    /** Custom (schema 1) effects of resource packs. */
    Collection<CustomEffectDef> customFromPacks();

    /** Reads the custom effects of the config folder again. */
    void reloadCustom();

    /** Writes the custom effects to the config folder (the editor's Save) and uses them. */
    void saveCustom(Collection<CustomEffectDef> defs);
}
