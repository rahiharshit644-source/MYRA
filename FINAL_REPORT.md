# MYRA Blue Glass redesign — final delivery report (cumulative patch, Phases 2–5)

STATUS: all code was written and statically checked only (brace balance, composable-scope check). It was NOT compiled,
unit-tested or run on a device in the sandbox (no Gradle/network/emulator). Treat every item below as "implemented, unverified".

## First steps on your machine
1. Unzip over the project root.
2. ./gradlew :app:testDebugUnitTest :app:assembleDebug   (send me any compiler errors; expect a few small ones)
3. Install, then walk the checklist at the bottom.

## What exists
- Dual theme: Classic MYRA (original, default for everyone incl. new installs) and Blue Glass. Settings -> Appearance & Themes,
  persisted in AppSettings("app_theme_mode"), reactive (no restart), data/permissions/keys untouched.
- Tokens: ui/theme/{MyraThemeMode,MyraTokens,ThemeController,OrbPalette}.kt. Classic tokens == original constants (ClassicFidelityTest).
- Components themed via tokens: GlassCard, buttons, chips, inputs, shadows, text colours, screen backgrounds, native orb halo + overlay orbs.
- Bottom nav: Classic unchanged; Blue Glass = Home, Chat, Browser | mic | Devices, Settings (mic is real: mute/unmute/connect/permissions).
- New screens: Chat (real engine), Weather (real wttr.in via location or typed city), Memory (real Memory2 DB, edit/delete w/ confirm).
- Home (Blue Glass): shortcut cards for Browser, Smart Home, APIs Hub, Weather, Memory, Logs.
- Smart Home: Quick Controls (All On / All Off / Add Device) -> real MQTT publishes with honest feedback.
- Existing screens recoloured by tokens (layouts/logic untouched): Smart Home, Add Device, Voice ID, Voice profiles, Permissions,
  Settings/Control Center + all sections, Storage/RAG, Orb settings, APIs Hub, Browser accents, Logs/Diary/Tasks/Onboarding backgrounds.
- Tests written (not run): ThemePreferenceTest, ThemeTokensTest, ClassicFidelityTest, WeatherParserTest.

## Known gaps (honest)
- Permissions, Notifications & Direct Reply, API & Persona, Voice ID, Control Center keep their EXISTING layouts (recoloured, not
  rebuilt to the reference's exact row/toggle designs). All their real functionality is preserved.
- Browser: engine untouched; accents themed; the reference's browser chrome (quick-launch grid, voice prompts, tab strip) not rebuilt.
  The user's browser wallpaper setting is respected, so it may stay lavender in Blue Glass until changed.
- WebGL orb on Home keeps its own colours (only the native halo/chip is recoloured).
- Weather: no air-quality tile (data source doesn't provide it). Weather/Memory/Chat reachable from Blue Glass Home/nav only.
- No reduced-motion handling added; no real backdrop blur (translucent surfaces used, which is the lightweight fallback).
- Tablet/landscape layouts not specifically tuned.

## Regression checklist (run on a device, both themes)
[ ] Fresh install -> Classic. Existing install update -> still Classic.
[ ] Settings -> Appearance: switch to Blue Glass and back; restart app; selection persists; chat/memory/keys/devices unchanged.
[ ] Classic: compare Home, Browser, Settings, Smart Home against old build (should be pixel-identical except nothing).
[ ] Blue Glass nav: each tab; mic mutes/unmutes when live; reconnects when offline; opens permissions if mic not granted; no mis-taps.
[ ] Chat: send typed message, get real reply, copy, retry, offline banner, keyboard doesn't cover composer.
[ ] Weather: deny location -> message + city search works; allow -> real data; airplane mode -> error state.
[ ] Memory: tabs, search, filters, edit, delete confirm, sensitive masking.
[ ] Smart Home: toggle a relay; All On/Off with broker disconnected shows failure; devices persist across theme switch.
[ ] Overlay orb: switch theme while overlay is running -> orb recreated in blue; Classic back to rainbow.
[ ] Rotation, font scale 1.3x, gesture nav, small phone (360dp): no clipped controls.
[ ] Secrets: API keys masked in Settings; nothing sensitive in logcat.

## v2 update: exact reference look
Colours were sampled from the reference image: primary #2C7FFC, mic ring #35B6FD, buttons gradient #2368F0 -> #3E9BFD, switch OFF #CBD8EE,
background #BFE1FC -> #E7F5FD with soft cloud glows (ui/theme/MyraBackground.kt), frosted white cards with bright white edge.
Blue Glass bottom bar now matches the reference (icon over label, white bubble on selected, big glowing mic). Glossy blue avatar orb in Home + Chat.
Switches are blue/white-thumb in Blue Glass; Classic switch colours unchanged.

## v3: fixes from your CI build log (3 compile errors)
- MyraOrbState.kt: enum needed ';' before the colorFor() function.
- MyraBrowserScreen.kt: theme colours were read inside an onClick lambda; now read once in composable scope.
- VoiceScreen.kt: theme mode was read inside LazyColumn content; now hoisted to a val.
