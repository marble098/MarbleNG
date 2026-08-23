# MarbleNG Kinetic Glass UI

MarbleNG's control surface is a Compose-based, white-first command interface. The visual system is
formal and information-dense, while electric blue, violet, emerald, amber and rose are reserved for
state, focus and motion.

## Runtime structure

`MarbleApp` applies `AetherFlowTheme`, which installs the Material 3 color scheme and one
`ProvideMarbleMotion` instance. `Aether2026App` then renders the three primary destinations:

- **Home** — connection state, live route telemetry, Iran Mode and high-frequency actions.
- **Library** — sources, node search, testing, ranking and per-node management.
- **Settings** — simple controls first, with expert networking sections disclosed on demand.

All screens read observable state directly from `AppRepository`. The UI never owns VPN, benchmark
or routing state.

## Motion engine

`MarbleMotion.kt` is the only motion authority.

### Physics vocabulary

`MarbleMotionSpecs` contains typed spring specifications for:

- direct press response;
- content response and exit;
- color;
- dimensions;
- spatial page movement;
- layout-size changes; and
- determinate progress.

UI code does not define arbitrary millisecond tweens. This keeps navigation, toggles, progress and
expand/collapse movement coherent.

### Shared ambient clock

`ProvideMarbleMotion` owns one `withFrameNanos` loop and exposes normalized `loop()` and
`breathe()` values through `MarbleMotion.current`. The backdrop, connection orbit, active Iran
Mode scan, indeterminate progress and active health orb all read that same clock.

Idle node rows do not read the clock. There is no permanent infinite transition per row.

### Direct manipulation

`Modifier.kineticClickable()` combines click semantics, the current indication and spring-backed
press scale/lift in one gesture owner. It is used for navigation, primary buttons, actionable cards,
node controls, accordions, switches and checkboxes. Callers must not stack another clickable
modifier on the same control.

### Reduced motion

Before starting the shared frame loop, the provider reads Android's global animator duration scale.
If animations are disabled, ambient motion freezes. Compose spring animations continue to follow
the platform duration-scale behavior.

## White glass material

The light palette in `AetherTheme.kt` is the product default.

| Token | Purpose |
| --- | --- |
| `Void` | cool-white application field |
| `VoidElevated` | near-white elevated layer |
| `Glass` | translucent white panel |
| `GlassStrong` | high-legibility glass panel |
| `GlassBorder` | white specular edge |
| `GlassBorderSoft` | blue-slate structural line |
| `Cyan` | electric primary action/state |
| `Amethyst` | secondary intelligence state |
| `Emerald / Amber / Danger` | success, caution and failure |

`HoloGlass`, the floating dock, primary buttons, Home portals, the connection hero and settings
accordions use layered gradients, a specular edge and restrained elevation. The animated background
uses low-opacity radial light and architectural diagonal lines so the surface stays bright without
becoming decorative noise.

Dark mode remains an explicit choice and preserves the same hierarchy and accent meanings. New
installs start in **Glass White**; users can select System or Dark in Appearance.

## Performance rules

- Keep exactly one ambient frame clock under `ProvideMarbleMotion`.
- Do not add `rememberInfiniteTransition` to lists or cards.
- Animate only active/probing node health indicators.
- Keep continuously moving color fields behind content, not in every surface.
- Use typed `MarbleMotionSpecs` instead of local `tween` values.
- Keep expensive repository and file operations outside composition.
- Preserve stable keys in lazy lists.

## Functional invariants

The redesign changes presentation and gesture feedback only. It does not alter:

- VPN permission or service startup;
- Xray/HEV lifecycle;
- benchmark coverage or scoring;
- subscription persistence;
- routing and DNS policy;
- Iran Mode detection;
- diagnostics; or
- release signing and native build preparation.

