# MarbleNG Expressive Motion v186

`MARBLE_EXPRESSIVE_MOTION_V186` is the motion chapter of the Material You upgrade V185 began.
Where V185 moved the product's *surfaces* — type ramp, containers, borders, tonal finish — onto
the newest Google Material language, V186 moves its *movement*: every arrival, press, release,
progress indicator, state flip and page turn in MarbleNG now speaks Material 3 Expressive, the
motion dialect shipping on current Android. No behavior, engine, routing, or storage path
changes; the compatibility line does not move either — **minSdk stays 26, target/compile stay
37** — because the whole language is built from primitives every supported device already has:
`CubicBezierEasing`, `spring`, `tween`, `Animatable`, `graphicsLayer` and `Canvas`. Nothing in
this chapter depends on an `androidx.compose.material3.expressive` artifact.

## Why motion deserves its own library

Before V186, Marble's motion lived in two places: `MarbleMotion.kt` (springs, the shared frame
clock, `kineticClickable`) and dozens of hand-written `tween(...)` / `spring(...)` calls at each
call site. That produced good *individual* animations and an inconsistent *language*: one press
released on a stiff spring, the next on a 200 ms tween; one spinner rotated at fixed width while
its neighbour breathed. Expressive motion is a system of named curves, named durations, named
springs and shared components — so V186 adds exactly that system as one new file, and rewires
the product through it.

### `MarbleExpressiveMotion` — the token table

The duration and easing tokens of the Material 3 Expressive spec, as compile-time constants:

| Group | Tokens |
|---|---|
| Durations | `Short1..Short4` (50/100/150/200 ms), `Medium1..Medium4` (250–400 ms), `Long1..Long4` (450–600 ms), `ExtraLong1..ExtraLong4` (700–1000 ms) |
| Curves | `Emphasized`, `EmphasizedDecelerate`, `EmphasizedAccelerate` — the three cubic-Bézier curves current Android uses for everything that enters, everything that leaves, and everything that travels |
| Cascade | `StaggerStepMs = 45` — the beat between two arrivals in a cascade |
| Arrival | `EntranceWindowMs = 700` — how long a screen's entrance cascade may play before it disarms itself |

The emphasized *pair* is the core grammar: things arriving decelerate (fast out of the gate,
soft landing), things leaving accelerate (slow lift-off, fast exit). A state change is therefore
legible without reading a single pixel of copy — the new value visibly *lands*, the old one
visibly *goes*.

### `MarbleExpressiveSpecs` — the physical vocabulary

Ready-made specs so no call site invents physics:

- `PressInFloat` — the quick, damped press-in of a touch target;
- `SpringReleaseFloat` (damping 0.42, stiffness 560) — **the** release of the chapter: a spring
  with one visible overshoot past rest. Every disc, pill, knob and button in the product now
  lets go with this beat instead of a linear return;
- `SpringPopFloat` — the acknowledgement pop of a selected/activated element;
- `ProgressSettleFloat` — determinate progress that arrives a hair past its target and settles;
- `WaveSpringFloat` (damping 0.58, stiffness 320) — the gentler wave spring for direct
  manipulation (slide-to-connect knobs, chevrons) where a big overshoot would fight the finger;
- `SpringReleaseDp`, `MorphPressDp`, `MorphReleaseDp` — the same physics for size-valued state
  (button height, shape morphing);
- `EntranceFadeFloat`, `EntranceRiseDp`, `EntranceRiseSpatial`, `RollInSpatial`,
  `RollOutSpatial` — the entrance/roll pieces used by cascades and rolling readouts;
- `EmphasizedColor`, `EmphasizedFloat` — state color/float changes on the emphasized curve.

### `ExpressiveMath` — pure, tested geometry

All the shape math of the wavy language as pure functions (no Compose, no composition — plain
Kotlin, exhaustively unit-tested in `MarbleExpressiveMathV186Test`):

- `wave(phase)` — the cosine wave normalized to 0..1: the heartbeat of every stretching loader;
- `wavyValue(phase, min, max)` — any quantity oscillating between bounds on that wave;
- `arcSweep(phase, minSweep, maxSweep)` — the sweep angle of a *stretching* arc: the signature
  of the newest Android spinners, where the arc grows long, snaps short, and grows again while
  it rotates, so an in-flight task never reads as a stuck ring;
- `staggerDelayMs(index)` — cascade delay, clamped at `STAGGER_MAX_INDEX = 6` so the last items
  of a long list arrive together instead of trickling forever;
- `morph(t, pressed)` — the squash-and-stretch corner-radius interpolation of a pressed button;
- `depthScale(pageOffset)` / `depthAlpha(pageOffset)` — the pager depth curve: a page travelling
  away shrinks to 0.90 and fades to 0.35 at the fully-offset extreme;
- `pointOnCircle(...)` — leading-dot trigonometry for determinate arcs;
- `wrap01(phase)` — phase arithmetic for offset rhythm rings.

Because these are pure, the *determinism* promise of the product survives the elastic surface:
a ping of 40 ms still maps to the same emerald band, the same arc, the same number — only the
*arrival* of that number gained physics.

## The components

- **`MarbleExpressiveCircularIndicator`** — the wavy spinner. Indeterminate: `arcCount` arcs
  (default 4) rotating on the shared clock, each stretching on `arcSweep` with an offset phase
  so the rhythm never repeats within one rotation. Determinate: a settle-spring progress arc
  with a leading dot. Both fall back to a calm static ring when animations are disabled.
- **`MarbleExpressiveLinearIndicator`** — the travelling-blob bar: indeterminate sweeps a blob
  whose *width itself* breathes on the wave; determinate settles on `ProgressSettleFloat`.
  Replaces every stock `LinearProgressIndicator`/`CircularProgressIndicator` in the product —
  the busy top bar, the probe strip, dialog loaders, refresh and testing spinners down to 11 dp.
- **`MarbleExpressiveValueText`** — the rolling readout: a single-line value that *rolls* on the
  emphasized pair inside a fixed slot — the new value decelerates up, the old accelerates down.
  Now used by every live number: the home ping badge, the live ping meter headline, the metric
  cards, the connection speed readouts.
- **`MarbleExpressiveStatePulseDot`** and **`MarbleExpressiveGlyphSwap`** — a pulsing state pip
  and a rotate-and-scale icon exchange for controls whose glyph changes with the session.

## The modifiers

- **`marbleStaggerIn(index, enabled)`** — the arrival cascade: rise 18 dp + scale from 0.96 +
  fade, delayed by `staggerDelayMs(index)`, on the emphasized entrance pair. `enabled` is a
  lambda so the caller decides *when* an arrival may play.
- **`rememberMarbleEntranceWindow(700)`** — the self-disarming gate every cascade uses: armed at
  first composition, disarmed forever after the window. A screen arrives once; scrolling back,
  filtering, reordering or recomposing never replays the entrance.
- **`marblePopWhen(trigger, peak)`** — the acknowledgement beat: when `trigger` changes, the
  element springs up past rest once (`peak`, typically 1.05–1.28) and settles. Selection,
  activation and session flips now *feel* confirmed under the finger.
- **`marblePageDepth`** — pager-page parallax: each page reads its own offset from the
  `PagerState` in the draw phase and applies `depthScale`/`depthAlpha` + a horizontal drift, so
  swiping between Home / Servers / Settings has real depth instead of a flat slide.
- **`expressiveClickable`** — press-in on `PressInFloat`, release on `SpringReleaseFloat`.
- **`kineticClickable`** (existing) gained an additive `releaseSpec` parameter — every existing
  call keeps its default, and the expressive call sites opt into the bouncy release.

## The transitions

- **`expressiveContainerTransform(forward)`** — the detail expansion: Home → connection detail
  now grows the new page out of the old with a shared fade-through, scaled on the emphasized
  pair, drifting a twelfth of the width in the travel direction.
- **`expressiveSharedAxisX(direction)`** — Settings' sub-page stack rides the shared X axis: the
  outgoing page accelerates out a seventh of the width, the incoming decelerates in from the
  same side the gesture implies, forward and back mirrored.
- **`expressiveFadeThrough`** — the neutral swap for equal-rank content.

## Where the language landed

**Global chrome.** The three-page pager gained depth (see `marblePageDepth`); the dock's tab
glyphs pop on selection (`marblePopWhen`, peak 1.18); the busy state of the compact top bar is
now the expressive linear indicator; page turns into the connection detail run the container
transform; Settings' sub-pages run the shared axis.

**Home.** The status word *rolls* instead of snapping — "CONNECTING" decelerates up into the
banner's fixed 18 dp line while the old sentence accelerates away. The national-event banner
and the speed widget arrive on the entrance-rise pair and leave on the accelerated roll. The
connection orb's securing arc stretches on `arcSweep` while it rotates; its glyph pops on every
state title change; its action discs release on the bouncy spring. `MiniMetric`, the bento
cards, the probe strip and the live progress bar all moved onto the shared components; the
bento cards cascade 1-2-3 on arrival.

**All four home themes cascade on arrival** — banner, content, control, one stagger step apart:
the slider theme (banner → server card → slide control), the floating theme (banner → expanded
list), the embossed theme (banner → orbital core → caption → server card), and the modular
customizer (every module in the user's configured order, socks card last). The Home server card
owns its own inner cascade too: its rows rise one step apart the first time it composes, and
`animateItem` still owns every reorder.

**The connect controls.** All five silhouettes speak the release spring now. The round button's
securing arc stretches between 74° and 136°, its power glyph rolls through state flips, and the
whole face pops once when the session lands. Both slide controls (theme 1's thumb, the classic
knob) spring back from a short drag on `WaveSpringFloat` — one visible overshoot that squishes
against the track's own clip, then holds; the committed flight keeps its deliberate tween,
because *that* beat belongs to the action, not the finger. Theme 1's invitation sheen breathes
between 22 % and 42 % of the track while it sweeps. The floating FAB and the orbital dial both
gained the stretching busy orbit, the acknowledgement pop and the bouncy release. The stream
bar's travelling ribbon became a *blob*: its width swells and thins once per pass.

**The status pip became alive.** `StatusDot` — the 13 dp pip inside the compact banner — keeps
its exact call signature and geometry, but a settled session now emits one soft ring every
2.6 s with a second ring half a period behind it, both expanding strictly inside the pip's own
slot. A protected tunnel reads as *alive* at peripheral vision, never as a frozen glyph.

**Servers.** The library page arrives as a cascade: group headers one stagger step apart, node
rows following inside their group (first eight only — the rest are below the fold). The group
header chevron rides the wave spring. The probe strip is the expressive linear indicator; the
ping capsule's testing spinner is a two-arc wavy ring; every round button releases bouncy and
pops when selected.

**Settings.** The hub's five cards cascade behind the header on arrival; the custom dock page
cascades its PULSE / SOURCE / CONFIG sections; the permission dialog arrives icon → headline →
explanation → assurance → error, one step apart, with the window disarming so a mid-life step
change never replays it.

**The design system itself.** `PrismButton` presses with a *morph*: its corner radius squeezes
on `MorphPressDp` and releases on `MorphReleaseDp` — the shape-memory press of the newest
Android buttons. `PrismIconButton` and `PrismSelectionTile` pop; `PrismPanel` accepts an
`entranceIndex` so any panel can join a cascade; `MarbleMetricCard` rolls its value;
`PrismConnectionStage`'s securing arc stretches.

## Guarantees this chapter keeps

1. **Fixed-slot contracts survive.** The live ping meter's reveal is still opacity-only
   (V135): the emphasized curves changed *how* it fades, never *that nothing around it moves*.
   The compact banner keeps its two-row geometry in every state. Dock slots, slide thresholds
   (65 % / 78 %), the slide-park behavior (V146) and the reserved 150 dp meter column are
   untouched.
2. **Determinism survives.** Semantic colors and metric bands remain pure functions of the
   evidence — the elastic layer animates *arrival*, never *value*.
3. **Accessibility survives.** Every loop, wave and pulse reads `MarbleMotionState`
   `.motionEnabled`: with system animations off, spinners hold a calm static arc, the status
   pip's rings rest, stagger delays collapse, and springs become the plain target value. The
   shared frame clock (V112) still drives all ambient motion in the draw phase, so none of it
   costs recompositions.
4. **API 26 survives.** No artifact bump, no version-gated branch: emphasized curves are
   `CubicBezierEasing` constants, springs are `androidx.compose.animation.core.spring`, the
   wavy loaders are `Canvas` arcs on the existing clock.

## Tests and invariants

`MarbleExpressiveMathV186Test` pins the pure math: the stagger clamp at index 6, the wave's
0..1 bounds and symmetry, `wavyValue`'s endpoint exactness, `arcSweep`'s bounds, the morph's
pressed/released endpoints, the depth curves' extremes, and `wrap01`'s period. The structural
preflight (`scripts/system-integrity-check.py`) gained a V186 block pinning the library's
existence (`object MarbleExpressiveMotion`, `object ExpressiveMath`), its adoption in the
product UI (the wavy indicators, the cascade modifier), the named test class, and this chapter.
Every touched file carries the `MARBLE_EXPRESSIVE_MOTION_V186` tag at the site of each change,
so the next chapter can find — and keep — every seam this one sewed.
