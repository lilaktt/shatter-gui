# Third-party

Shatter GUI — © 2026 lilaktt, Apache License 2.0 (`LICENSE`, `NOTICE`).

## Spatial GUI (MIT) — https://github.com/tastytrash/Spatial-GUI, by tastytrash

The idea was inspired by Spatial GUI. For the integration its code (1.8 and 1.9) was only read, to find where its
plane is and how its close animation works; no code from Spatial GUI was copied. Shatter GUI calls public methods of
Spatial GUI and sets two of its private fields through `@Accessor`, only when Spatial GUI is installed; it does not
depend on it and does not include it in its jar.

Checked (27.10.2026): the source of Shatter GUI contains no copied parts of Spatial GUI (the `compat/spatial` package
only calls its API by name), so the MIT condition "include the copyright and the license text in copies" does not
apply. Thanks and a link are in `NOTICE`, in the "About" section and in Mod Menu (contributors). The Spatial GUI 1.8/1.9
jars have no LICENSE file and an empty `authors` list, so the repository owner is named; no copyright line was made up.

If code from Spatial GUI is ever taken, the MIT license text and its copyright line must be added here and to `NOTICE`
word for word.

## Iris — https://github.com/IrisShaders/Iris (LGPL-3.0)

Shatter GUI calls only the public API of Iris (`net.irisshaders.iris.api.v0.IrisApi`) when Iris is installed; no code
from Iris is copied or included in the jar.

## Development-only dependencies (not in the mod's jar, not in the repository)

Downloaded by Gradle from the Modrinth Maven (`build.gradle`):

- Spatial GUI 1.8 (`compileOnly`, and 1.8 or 1.9 for runs with `-PspatialGui=…`), MIT.
- YACL 3.9.6 — a dependency of Spatial GUI for those runs, LGPL-3.0.
- Iris 1.11.4 (`compileOnly` for its public API, and for runs with `-Pshaders`), LGPL-3.0; Sodium 0.9.2 — only for
  runs with `-Pshaders`, PolyForm Shield 1.0.0.
- Mod Menu 18.0.2 (`compileOnly` for its API, and for runs with `-PmodMenu`), MIT.

Shader packs (Bliss, Complementary, BSL) are not part of the project: the game test copies the pack given with
`-PshaderPack` into its own run folder.
