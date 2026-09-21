# MarbleNG Material Refresh v185

`MARBLE_MATERIAL_YOU_REFRESH_V185` is a visual, design and UX overhaul that moves the whole
product onto the newest Google Material language — the Material You / Material 3 Expressive
finish that ships on current Android — while holding the compatibility line exactly where it
was: **minSdk stays 26, target/compile stay 37, and every dynamic-color branch keeps its
API 31 guard.** No behavior, engine, routing, or storage path changes; this chapter is design
system only.

## What changed

### Typography — a clearer hierarchy

The type ramp follows the Material 3 scale with the compact product calibration MarbleNG ships:

| Role | Before | After |
|---|---|---|
| displayLarge | 36 / 42 | **40 / 48** |
| displayMedium / displaySmall | — | **34 / 42, 30 / 38** (new roles) |
| headlineLarge / Medium / Small | 25 / 22 / 19 | **28 / 24 / 20** |
| titleLarge / Medium / Small | 17 / 15 / 13.5 | **18 / 16 / 14** |
| bodyLarge / Medium / Small | 15 / 14 / 12 | **16 / 14 / 12** |
| labelLarge / Medium / Small | 13.5 / 12 / 11 | **14 / 12 / 11.5** |

Every step between adjacent roles is now at least 1.5 sp, so display → headline → title → body
reads as an unambiguous ladder at a glance. Line heights gain ~1 dp of air, tracking follows the
Material spec (negative for display/headline, slightly positive for body and labels), and the
Persian floor logic survives unchanged: Vazirmatn renders one half-step above the Latin label
floor (12 sp vs 11.5 sp) with neutral tracking, exactly as V184 established.

### Colors — new cards, containers and borders

The navy/ice/electric brand ramp is untouched; what changed is the finish of every role that
holds content.

**Light** — Material You's calm container language:
- page: `#F0F8FF` alice → **`#F4F8FD`** cool near-white (page gradient `#F8FBFE → #EDF4FB`);
- containers: the greenish ice-white glass steps → **`#EDF3FA` / `#E2ECF6` / `#E8EFF7`**;
- borders: the 30 %-electric rim → a **22 %-electric soft stroke** plus a **neutral navy
  hairline** (`#000033 @ 10 %`) for quiet dividers;
- ink secondary/faint drop to 66 % / 40 % so primary content owns the contrast.

**Dark** — navy-lifted steps over AMOLED black:
- elevated cards: the invisible `#020204` step → **`#0B111C`**, containers `#0E141F` /
  `#121A28`, wells `#101724` — cards separate from the void the way current Material dark
  themes do;
- hairlines cool to slate-blue (`#1E2836`);
- Home cards `#0F1727` with a `#25344E` rim, selection rim brightened to `#6FB2F2`.

Emerald / amber / red stay strictly semantic (connected / degraded / blocked) — a VPN must
never let the brand hue say "healthy".

**Stock Material components** now resolve the full tonal container ladder
(`surfaceContainerLowest…Highest`, `surfaceDim/Bright`, `inverse*`) to the Marble ramp in both
brightnesses, so bottom sheets, menus, chips and filled cards stop wearing the stock gray
baseline and sit inside the same identity as the custom Prism surfaces.

### Borders, radii and spacing

- Shape ramp rounds one step up: **12 / 16 / 20 / 28 / 32 dp** (`MaterialTheme.shapes`), and
  every custom surface follows: cards 20 → **22 dp**, tiles 16 → **18 dp**, insets 12 → **14 dp**,
  controls 13 → **15 dp**, Home cards 22 dp with 14 dp insets.
- Touch targets meet the Material baseline: standard controls **46 dp** (was 44), compact
  **36 dp**, icon controls **42 dp**, selection tiles **42 dp** with roomier padding; the detail
  button grows to 54 dp.
- Hairlines: card rims soften (`#DEE8F3` light / `#25344E` dark), the strong hairline steps to
  1.5 dp, and the Home selection rim deepens to `#3D8BE0` for a higher-contrast single accent.

### Icons — larger, bolder, modern

- The stroke curve lifts to the Material Symbols optical weight (~9.4 % of the glyph box,
  clamped 1.5–3.8 dp) in `HomeVectorIcon`, `MarbleTabIcon` and `HomeGlyphIcon` — every icon in
  the product reads fuller without redrawing a single path.
- Sixty inline icon slots across all screens render 1–3 dp larger via one systematic pass;
  micro-badges (10–12 dp dots) stay untouched so they keep their optical size.
- Leading icon tiles grow to a **42 dp square with a 22 dp glyph**; the header brand mark grows
  to 40 dp / 24 dp.
- The floating dock: **+2 dp taller (52/64/76), corners 26/30/34, glyphs 19/24/29** — page
  clearance derives from the same metric, so lists reserve the new footprint automatically.

## Files

- `app/src/main/java/com/marbleng/app/ui/AetherTheme.kt` — type ramp, shape ramp, light/dark
  palettes, full Material container ladder, launch window color (in `styles.xml`).
- `app/src/main/java/com/marbleng/app/ui/MarbleDesignSystem.kt` — `PrismSurface` tokens,
  `HomeCloud` surface system, button/tile/well/badge geometry.
- `app/src/main/java/com/marbleng/app/ui/Aether2026.kt`, `MarbleHomeStyles.kt`,
  `MarbleHomeStudio.kt` — icon weights and sizes, dock metrics, card radii.

## Deliberately unchanged

- minSdk 26, target/compile 37, ABI splits, signing.
- Semantic state colors and their metric bands (and the tests that pin them).
- All interaction logic: the dock still never moves, cards still never resize on state,
  nested translucency and stacked shadows stay banned, and the one-shadow-one-hairline depth
  contract is untouched.
