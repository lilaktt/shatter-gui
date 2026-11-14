# Changelog

## Unreleased

- Glass pieces in water stay visible: under water and on the bottom they show through the water, tinted by it, from above
  and from inside the water, with and without a shader pack and with Spatial GUI. Before, a piece that came to rest under
  water vanished long before the end of its life, because the water surface was drawn in front of it. A solid block
  between the camera and a piece still hides it, in water too. In water the pieces sink slowly: gravity ×0.35 and more
  drag (glide ×0.8).
- Opening effects play in the creative inventory. The game replaces the survival inventory with the creative one while
  opening it, and the opening was skipped (with or without Spatial GUI, on 26.1.x, 26.2 and 26.3). With Spatial GUI the
  opening plays on its plane: Spatial GUI's own plane and opening animation appear only once our effect has put the
  screen together.
- Spatial GUI, third person: closing a screen no longer leaves the screen empty for about 0.3 s before the pieces appear
  (on 26.1.x and 26.2; 26.3 was fine). The first frame of the pieces now matches the last frame of Spatial GUI's plane:
  same place, angle and size, in first and third person.
- Pieces in flight no longer cover the player's model or other mobs standing in front of them: a piece behind a model is
  hidden by it, a piece in front still shows over it. This is the same with and without Spatial GUI and with a shader
  pack.
- Pieces behind a wall: the exception for pieces that start behind a block (you stand right at a wall) ends once the
  camera has moved more than a block. With Spatial GUI in third person the camera stands away from the player, and
  pieces kept showing through walls for their whole flight. Pieces in flight in the open still show over blocks.
- About: the Modrinth and CurseForge buttons show 8×8 icons drawn by the mod's author, on the text row, 8 GUI pixels wide
  and sharp at every GUI scale; coloured icons keep their colours and lighten a little on hover, light ones would be
  tinted. A picture of the wrong size or a damaged file falls back to the built-in icon with one line in the log. The
  loader needs one `<name>.png` (the 16×16 and `_32` pictures are gone). GitHub and "Report a bug" keep the built-in icons.
- Pieces in flight behind a wall or roof are hidden by it once a solid block is between them and the camera, with or
  without a shader pack (before, they showed through the blocks until they settled, about 1–2 seconds). Pieces that start
  behind a wall in front of you, and "Fly" pieces flying into their container, still show over blocks.
- Spatial GUI 1.11.0 and 1.11.1 work again: they renamed the class that draws the 3D plane, so the bridge failed at the
  first close and Spatial GUI played its own closing animation next to our effect (on 26.1.x, 26.2 and 26.3). The bridge
  no longer checks version numbers: at start it looks for the classes, methods and fields it uses and turns itself on only
  when they are all there. One line in the game log says which Spatial GUI was found and whether the bridge is on and why;
  the same line is in About (small print) and in "Copy diagnostics".
- `./gradlew verifyDist` starts the game with each `dist/` jar in a clean `mods/` folder with Spatial GUI and checks the
  bridge on the jar itself.
- The CurseForge link (About, Mod Menu, `fabric.mod.json`, README) now points to
  https://www.curseforge.com/minecraft/mc-mods/shatter-gui. The project's addresses come from one place in the code, so
  `fabric.mod.json` and About can no longer disagree.
- Pieces never cover the player's hand, with or without a shader pack, on 26.1.x, 26.2 and 26.3. Before, pieces close to
  the camera (the first moments of a close, pieces lying a block or two away) showed over the hand, and with a shader
  pack every piece in flight did.
- Settled pieces no longer flicker at their edges: a piece at rest was turned by a slightly different amount every frame.
- Release jars come from `./gradlew dist` only (`dist/`, with `SHA256SUMS.txt`); each jar names its commit and build time
  in the game log at start, in Settings → About and in "Copy diagnostics".
- "Draw over blocks" now applies to pieces in motion only: a piece that has settled on the ground (or is older than 5
  seconds) is hidden by walls and the block above it like the rest of the world, and the switch shows nothing on screen.
  The same for the overlays and particles of data effects. Off: unchanged.
- Glass shards land on their lowest corner and fall over onto a face, so they lie flat on the ground instead of half in it.
- With a shader pack, pieces on the ground now bob with the world while walking, sprinting and when hurt (they stood
  still while the world moved).
- About: the Modrinth, CurseForge, GitHub and "Report a bug" buttons fill with their platform's colour on hover and keyboard
  focus (the text stays readable), have small icons (the mod's own; platform logos can be put into
  `assets/shatter_gui/textures/gui/links/`) and a dot saying whether the page is up: green, yellow (not published yet or in
  moderation; the button explains before opening it), grey (could not check). It asks Modrinth and GitHub once per session,
  only while About is open; "Check the mod's pages" (`linkStatusCheck`) turns it off.
- Minecraft 26.2 and 26.3: separate jars from the same code (`shatter-gui-<version>+mc26.2-fabric.jar`, `+mc26.3`), with
  Spatial GUI 1.10.1 and Iris + Sodium on each. On 26.3 both OpenGL and Vulkan work. Known: 26.3 on Vulkan sometimes
  crashes while loading resources at start, with or without the mod (3 of 20 starts with it, 2 of 20 without).
- One jar for every Minecraft 26.1.x: 26.1, 26.1.1 and 26.1.2 (`shatter-gui-<version>+mc26.1-fabric.jar`).
- The darkened world behind a chest, furnace, crafting table or inventory now fades in with the opening effect and
  out with the close effect instead of appearing at the end of the opening and vanishing at the start of the close
  (menus with a blurred background fade their blur too). The same with Spatial GUI and shader packs.
- The first frame of a close no longer shows the world without the interface for a moment: the pieces are there from
  the very first frame.
- Openings follow the camera: walking, sprinting or turning while an interface opens no longer leaves its pieces behind;
  they land exactly where the interface appears. New setting "Follow the camera" (General → Playback: auto / always /
  never) and `"followCamera"` for effect files; with it on, the fly close leaves from where the interface is now.
- Opening pieces are drawn over the HUD and the darkened background like the interface they become, and are no longer
  coloured by a shader pack (the colour jump at the end of an opening with shaders is gone).
- "Copy diagnostics" in Settings → About: versions, graphics card and driver, shader pack, chosen effects and the
  mod's last warnings for bug reports, without user names, file paths or e-mail addresses.

## 0.1.0-beta — 2026-10-31

First public release, for Minecraft 26.1.2 (Fabric).

- When you close an interface its last frame breaks into pieces that live in the world: 5 built-in effects (glass,
  burn, fly back into the block, dissolve, vortex) and 27 shipped data effects (frost, sand, ender portal, runes,
  lightning, glitch, paper plane, tiles, explosion, confetti, bubbles, soul fire, pixelate, blinds and 13 quick light
  ones: pop, slide, card flip, recede, door, ripple, twirl, drop, accordion, sticker, spring, tear, iris).
- Opening effects: the interface flies out of the block or gathers from its pieces (every effect that can play
  backwards), at most 0.35–0.5 s by default, with an "Opening speed" setting.
- Effects are drawn over the world by default ("Draw over blocks", `alwaysOnTop`): walls and floors in front never cut
  into them, the player's hand, the HUD and screens stay on top; the same with Spatial GUI and Iris shader packs
  (with a shader pack the hand is drawn under the pieces).
- Performance settings: effect quality (low / medium / high — low also skips block collisions, glow and texture
  overlays), how many effects play at once (1–8, default 2) and what happens over that limit (finish the oldest in
  about 0.2 s, or skip the new one). The style presets set them too (Subtle = low and one effect at a time).
- Thematic mode (off by default): furnaces burn, enchanting tables break into runes, ender chests vanish into a
  portal…; the table is editable in the Screens tab.
- Visual effect editor with live preview, timeline, curves, textures, particles and sounds; custom effects as JSON
  files or `.shatterfx` packages (format in `docs/EFFECT_FORMAT.md`).
- Works with Spatial GUI (its 3D plane breaks in place, first and third person) and Iris shader packs; Mod Menu shows
  a config button and links to Modrinth, CurseForge and GitHub.
- Accessibility: "Reduce motion", at most 3 flashes per second.
- 12 languages: English and Ukrainian by hand; German, Spanish, French, Portuguese (Brazil), Polish, Simplified Chinese,
  Russian, Japanese, Korean and Italian machine-assisted (corrections welcome).
- "About" in the settings: buttons to Modrinth, CurseForge, GitHub and "Report a bug" (through the game's link
  confirmation).

Fixed before the release:

- Pressing Esc in the world with an opening effect froze the picture for about 10 seconds before the pause menu
  appeared: the pause menu pauses a singleplayer game and its opening waited for game ticks. Openings now run while
  the game is paused; Esc is never delayed or lost. A close that takes over 50 ms logs one warning with the slow step.
- Profiling log lines are off by default (`"profiling": true` to turn them on).
- Labels cut off with "…" in several languages: the "For opening / For closing / For both / Remove" buttons and the
  page links wrap instead, and a few long translations are shorter (checked in all 12 languages at GUI scales 1–4).
