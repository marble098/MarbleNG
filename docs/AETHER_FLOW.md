# MarbleNG White UI

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
`breathe()` values through `MarbleMotion.current`. Active Iran Mode scanning, indeterminate
progress and active health orbs read that same clock. The page backdrop and idle connection control
stay still so motion communicates state instead of decoration.

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

## Solid white material

The light palette in `AetherTheme.kt` is the product default.

| Token | Purpose |
| --- | --- |
| `Void` | cool-white application field |
| `VoidElevated` | near-white elevated layer |
| `Glass` | opaque white compatibility surface |
| `GlassStrong` | opaque cool-white secondary fill |
| `GlassBorder` | subtle cool-grey edge |
| `GlassBorderSoft` | low-contrast structural line |
| `Cyan` | electric primary action/state |
| `Amethyst` | secondary intelligence state |
| `Emerald / Amber / Danger` | success, caution and failure |

`HoloGlass`, the floating dock, node cards, notifications, Home portals, the connection hero and
settings accordions use opaque fills and at most one soft outline. Nested transparency, specular
edges and stacked shadows are intentionally avoided because they caused GPU compositing bands on
some devices. Vivid accents are reserved for actions and live quality thresholds.

Dark mode remains an explicit choice and preserves the same hierarchy and accent meanings. New
installs start in **White**; users can select System or Dark in Appearance.

## Performance rules

- Keep exactly one ambient frame clock under `ProvideMarbleMotion`.
- Do not add `rememberInfiniteTransition` to lists or cards.
- Animate only active/probing node health indicators.
- Keep idle background and surfaces static; animate only an active state or direct response.
- Use typed `MarbleMotionSpecs` instead of local `tween` values.
- Keep expensive repository and file operations outside composition.
- Preserve stable keys in lazy lists.

## Functional invariants

The redesign primarily changes presentation and gesture feedback. DNS resilience additionally gains
multi-provider adaptive failover while preserving the user's exact list when Adaptive DNS is off.
It does not alter:

- VPN permission or service startup;
- Xray/HEV lifecycle;
- benchmark coverage or scoring;
- subscription persistence;
- routing policy;
- Iran Mode detection;
- diagnostics; or
- release signing and native build preparation.
