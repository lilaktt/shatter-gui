# Contributing

## Reporting a bug

Open an issue at https://github.com/lilaktt/shatter-gui/issues (the "Bug report" form asks for all of this):

- Minecraft version and Shatter GUI version (Mod Menu shows it, or the jar name).
- Your mod list (the `mods` folder or Mod Menu), with versions of Spatial GUI, Iris, Sodium and the shader pack if you
  use them.
- `logs/latest.log` from the session where it happened (and `crash-reports/…` after a crash). Attach the file rather
  than pasting parts of it.
- Steps to reproduce: what you opened or closed, which effect, what you expected and what happened instead. A short
  video helps with visual problems.
- If a setting matters, your `config/shatter_gui.json`.

For a stutter or a delay, set `"profiling": true` in `config/shatter_gui.json`, reproduce it once and attach
`latest.log`: the `[ShatterProfile]` lines time every step. Steps over 50 ms are logged as a warning even without it.

## Sending a translation

Language files are in `src/main/resources/assets/shatter_gui/lang/<code>.json`; `en_us.json` is the source of truth.
English and Ukrainian are written by hand, the other languages are machine-assisted and corrections are welcome.

1. Change the texts in your language file. Keep every key, every `%s` / `%d` / `%1$s`, and the terms in
   `docs/i18n/GLOSSARY.md` (if a term itself is wrong, change it there too).
2. Run `./gradlew test`: it fails if a key is missing or extra, or if placeholders differ from English.
3. Optionally check long labels in game: `./gradlew runClientGameTest -PtestOnly=lang -Plang=<code>` fails if a label
   is cut at GUI scales 1–4 (screenshots in `build/run/clientGameTest/screenshots`).
4. Open a pull request. Without git, an issue with the key, the current text and your text works too.

## Building

JDK 25, then `./gradlew build` (the jar is in `build/libs`, unit tests run with it). Game tests run a real client:
`./gradlew runClientGameTest`, see the README for the options. Development-only mods (Spatial GUI, Iris, Sodium, Mod
Menu, YACL) are downloaded from the Modrinth Maven and never bundled.
