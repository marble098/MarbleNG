# MARBLE_AURORA_SURFACE_V219 — the surface, the icons, and the whole window

Five complaints, one pass:

1. the icons in Settings look weak and ugly;
2. the app is not full screen — the area under the home indicator belongs to Android, not to Marble;
3. lines, boxes and the logo are visually weak;
4. the pull-to-connect bar looks dated;
5. everything else, bit by bit.

None of those are features. They are all *material*, so this chapter changes no behaviour: no probe,
no route, no persistence, no permission. What changed is the grid every icon is drawn on, the light
every box is lit by, the window the app draws into, and the control the user drags.

---

## 1 — One glyph grid, and every icon moved onto it

The previous icon system was a 500-line `when` in which every glyph had invented its own box. A 19 dp
Settings glyph and a 22 dp row glyph were therefore two different drawings: different optical
margins, different stroke weights (some branches resolved to about 1 dp — a grey smudge at 1.75×
density), and circles that were only round by accident at small sizes.

`MarbleGlyphArt.kt` replaces all of it:

- **A 24-unit grid.** Every path is authored in a 24 × 24 box, then scaled to the real icon size and
  centred, so every glyph has the same optical margin *by construction*.
- **One stroke rule.** `marbleGlyphStrokePx(size, weight)` is the only place a stroke width is
  computed: 1.75 grid units with round caps and joins, which is 1.3 dp at 18 dp, 1.6 dp at 22 dp and
  2.0 dp at 28 dp. The function is pure, so `MarbleGlyphArtV219Test` pins it on the JVM.
- **Duotone, not doodles.** Each glyph has one primary stroke and, where it helps the form read, one
  secondary stroke at 74 % of the weight — a shield's check, a palette's wells, a folder's lip. At
  16–20 dp a single-weight outline dissolves; two weights survive.
- **One container.** `MarbleGlyphTile` is the tile an icon sits on: a squircle at 32–34 % of its own
  size, an opaque tone-tinted fill, a specular wash from the top-left, a rim that is the tone lit
  above and quiet below, and a tone-cast shadow. The old 12 % colour circle had no light source,
  which is exactly what made the Settings icons read as flat stickers.

`HomeVectorIcon` and `HomeGlyphIcon` — the two hand-drawn sets the product actually used — are now
thin aliases over `marbleGlyphFor(...)` / `HomeGlyph.marbleGlyph`, so **every existing call site moved
onto the grid without changing a single call site**. The mapping is exhaustive, so a new icon cannot
be added without a glyph being chosen for it.

## 2 — The window is the app

- `MainActivity.enableEdgeToEdge(SystemBarStyle.auto(transparent, transparent) ×2)` — content is laid
  out behind the status bar and behind the gesture bar on every API level the app supports.
- `Theme.MarbleNG` no longer paints the bars: both are transparent, contrast enforcement is off, the
  display cutout is allowed to bleed (`shortEdges`), and icon appearance is set from the live palette.
- `AetherFlowTheme` additionally calls `WindowCompat.setDecorFitsSystemWindows(window, false)` and
  keeps a *transparent* status bar from Q up (pre-Q keeps the palette floor, because the platform
  would otherwise paint a grey scrim above the page on those tiers). The pinned legacy assignment is
  therefore still in the file, in the branch where it is actually correct.
- Insets are still consumed exactly once: the page stack pads the status bar, the floating dock pads
  the gesture bar, editors pad the IME, and `Scaffold(contentWindowInsets = 0)` stops the template
  from adding a second pass on top of them.
- **The home-indicator shelf.** A 168 dp vertical gradient at the bottom of the window lifts the page
  colour off the backdrop under the floating dock, so the gesture line sits on the app's own floor
  instead of on a raw gradient.

## 3 — Lines, boxes, and the mark

- **`Modifier.marbleLitSurface(shape, fill, tone)`** is now the one row skin: fill, a specular plane
  at the top edge, and a rim that is lit above and quiet below. A switch tile, a stepper, an
  information row, a flag chip and the servers strip all use it, which is why they finally agree.
- **Dividers fade.** `MarbleFadeDivider` and `MarbleNativeDivider` are gradient hairlines that are
  strongest where content starts and gone before the far edge. A flat rule draws a table cell.
- **Cards catch light.** Every card carries a white specular plane in addition to its tone wash — on
  a light page the lit stop of a rim is white, not the accent, because a coloured hairline on a
  coloured card is a border while a white one is a light.
- **The corner ramp moved onto the platform's steps** — 24 dp card, 20 dp tile, 16 dp inset — so a
  Marble card and an Android card describe the same silhouette.
- **The dock is milled**, not filled: a body gradient, a specular plane, a rim, and an active pill
  that is a lit lens rather than a tinted rectangle. Its tab marks are grid glyphs (8 % heavier when
  selected) instead of four hand-measured shapes.
- **The mark is drawn, not tinted.** `MarbleBrandMark` — a lit squircle with a cyan → amethyst ramp,
  a specular plane, an orbit ellipse, a folded "M" and one translucent facet — replaces the flat
  tinted PNG-style vector. It appears in the Home header beside the wordmark and in the custom page's
  top bar. The adaptive launcher icon, its monochrome themed layer and `ic_marble_prism` are the same
  prism, authored inside the 66 dp adaptive safe zone.

## 4 — The drag-to-connect control, rebuilt

`MarbleSlideSurface.kt` owns the material of both slide controls (the classic slider in
`ConnectButtonSlide` and the iOS-style one in `IosSlideToConnect`):

- **the well** — card fill, a tone-tinted top edge, a rim lit at the top, the tone's cast shadow;
- **the energy bed** — a gradient that ends exactly under the knob with a brighter head, so travel is
  legible without a second progress indicator;
- **guide marks** — three chevrons on the un-travelled side that fade as the thumb approaches;
- **the thumb** — a lit sphere: vertical tone gradient, specular cap, white hairline ring, tone-cast
  shadow. It is a physical object the finger pushes, not a coloured disc;
- **the label** — `marbleSlideLabelTone` contrast-checks the text against the card fill instead of
  assuming the accent is readable on it.

The gesture is untouched: `ConnectButtonSlide` keeps its V146 parking, its threshold, its haptic and
its V207 commit ordering (the command is dispatched before the follow-through animation), and
`IosSlideToConnect` keeps its RTL-aware drag sign. Only the material changed, which is why the two
clocks now show one control.

## 5 — The rest, bit by bit

- Settings group cards: a 46 dp glyph tile, a tone-tinted count badge with its own rim, a 30 dp
  disclosure disc that rotates, and one fading hairline between a group's header and its children.
- Settings rows: `SettingsRowGlyphPreview` puts the row's own glyph on a 30 dp chip, and the
  disclosure is a 26 dp disc — so a row ends on an object instead of on white space.
- `PrismPanel`, `PrismButton`, `MarbleNativeCard`, `MarbleNativeListItem`, `HomeCloudCard` got the
  specular plane, the lit rim ramp and the tightened corner steps.
- `PrismBackdrop` got one diagonal aurora band, so a long page has a horizon instead of three corner
  glows and a void.
- Bottom sheets: `MarbleSheetGrabber` — a 46 × 5 dp lit pill with a tone-cast shadow and a hairline —
  replaces the grey capsule in both the library filter sheet and the routing rule editor.

---

## Verification

- `python3 scripts/system-integrity-check.py` — the repository's 352 source invariants, including the
  full-screen backdrop, the one-inset-per-surface rule, the platform measurement table and the V207
  slide-commit ordering. The pass adds no new files to the gate's inputs that it does not already
  scan (`ui/*`), so the count is unchanged.
- `:app:testDebugUnitTest` — `MarbleGlyphArtV219Test` pins the glyph stroke rule at 16–28 dp, the
  selected-weight band, the sub-pixel floor, and the slide geometry (`thumb + 2 × inset ≤ track`).
- `:app:compileDebugKotlin`, `:app:compileReleaseKotlin` — the new files are pure Compose UI with no
  new dependencies.
