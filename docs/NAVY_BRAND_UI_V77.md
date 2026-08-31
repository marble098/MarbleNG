# MarbleNG — Navy Brand UI v77

The product identity is re-anchored on the Marble navy / ice / electric-blue ramp. Both the
White (light) and Dark themes are the **same formal color system**; only the surface/ink roles
swap. This is a design-system change only — it does not touch Xray, HEV, routing, DNS, identity
guard, benchmarking or any connection behavior.

## Brand palette

| Hex       | Role                             | Light                     | Dark                      |
|-----------|----------------------------------|---------------------------|---------------------------|
| `#000033` | deep navy                        | primary text ink          | page background (`void`)  |
| `#001144` | dark navy                        | secondary accent          | elevated surface          |
| `#0066CC` | electric blue                    | primary accent / actions  | secondary accent          |
| `#3399FF` | bright blue                      | highlight / gradients     | primary accent / actions  |
| `#ADD8E6` | ice blue                         | soft borders / washes     | muted ink / highlights    |
| `#E0FFFF` | ice white                        | tonal "glass" surface     | (not used as a fill)      |
| `#F0F8FF` | alice blue                       | page background (`void`)  | primary text ink          |
| `#FFFFFF` | white                            | elevated surface / cards  | (not used as a fill)      |

Every softer or deeper step (borders, recessed wells, muted ink) is produced by compositing one
of the eight hues **with alpha over a surface** (`Color.compositeOver`), never by introducing a
foreign hue. So a border is "electric blue at 30% over white", a muted label is "deep navy at
70% over white" — the identity stays inside the ramp at every step.

### Two accents, not one

- **Primary** (`Aether.Cyan`) = electric `#0066CC` in light, bright `#3399FF` in dark (dark
  themes lift the accent for contrast on navy).
- **Secondary** (`Aether.Amethyst`) = deep navy `#001144` in light, electric `#0066CC` in dark.
  Gradients such as the brand tile and section rails now run electric → deep navy, the "deep sea"
  signature.

### Status colors stay functional

Emerald / Amber / Danger are deliberately **not** recolored into blue. A VPN must never dress
"blocked" or "connected" in the brand hue — "connected = green", "degraded = amber", "blocked /
failed = red" remain deterministic safety semantics, exactly as the v53 design-system audit
requires.

## Home puzzle grid

Home now obeys one rhythm so the stack interlocks like puzzle pieces instead of drifting as
unrelated floating panels:

- a single **10dp** vertical gutter between Home cards;
- every internal grid (summary metrics, quick actions, metric cells) uses **8dp**;
- the Ping / Jitter / Quality readout is a **single bento panel**: one shared surface with three
  interlocking cells instead of three independent cards;
- the floating bottom dock gives the active tab a soft ice-blue pill (Material 3 Expressive
  floating-navigation pattern).

## Icon system

All glyphs are font-independent Canvas vectors drawn from one shared `HomeVectorIcon` engine:

- stroke weight is **size-aware**: proportional to the glyph with a soft floor/ceiling, so an
  icon is thin at micro/inline sizes (10–13dp) and confident at hero sizes (24–26dp) — each icon
  scales with its slot and keeps the same optical weight;
- icon "chips" (`HomeIconTile`) use a soft tone gradient plus a hairline so they read as crafted
  glass rather than flat tinted squares;
- the launcher mark, adaptive-icon background and theme previews all use the same eight colors.

## Modern Android design methods applied

The visual language follows the current Material 3 Expressive direction (announced at Google I/O
2025, shipping through 2025–2026):

- **Brand-owned color** — M3 Expressive explicitly supports apps that "stick to the brand colors"
  instead of following dynamic system color; Marble keeps its navy/ice ramp while the optional
  "System" theme still offers Material You dynamic color.
- **Motion physics** — springs for press/lift and page transitions (already in `MarbleMotion`),
  coherent rather than per-card tweens.
- **Tonal surfaces + soft outlines** — one shadow + one hairline for depth, recessed wells for
  insets, no stacked translucent layers (avoids GPU compositing bands on real devices).
- **8dp grid, shared radii** — consistent spacing and corner tokens so containers read as one
  system.
- **Adaptive layout** — mobile floating dock / compact tabs, wide NavigationRail at >= 700dp.

### Sources

- Google I/O 2025: Material 3 Expressive (Android 16) — component, motion, shape and color
  updates: <https://9to5google.com/2025/05/13/android-16-material-3-expressive-redesign/>
- Material 3 Expressive design tactics (emotion-driven color, motion, shape):
  <https://medium.com/@dharmakshetri/build-next-level-ux-with-material-3-expressive-9802f4dc1ae6>
- 2025 in review — M3 Expressive app rollout notes:
  <https://9to5google.com/2025/12/27/recap-material-3-expressive/>
