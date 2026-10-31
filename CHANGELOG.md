# Changelog

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
