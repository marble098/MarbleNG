# Marble UI Refresh V103 — Liquid Dock, Settings IA, AMOLED Black

Three related changes, all in the UI layer. No behaviour, networking, or settings-model change:
every switch still reads and writes exactly the same `AppSettings` field it did before.

---

## 1. Bottom navigation — `MARBLE_LIQUID_GLASS_DOCK_V103`

`FloatingSpatialDock` was rewritten from scratch (`ui/Aether2026.kt`).

**What was wrong.** The old dock was three independent `Column`s in a `Row` mounted as a
`Scaffold(bottomBar = …)`. Because it was a `bottomBar`, the Scaffold reserved opaque layout space
for it — so it could never actually float, and nothing ever passed behind it. Selection was three
separate pill/underline animations that faded in and out independently, so moving between tabs
looked like three unrelated fades rather than one object moving.

**What it is now.**

- **Floating.** The dock is an overlay inside the content `Box`, aligned to `BottomCenter`. Pages
  scroll *underneath* it. Every scrollable page reserves `MarbleDock.ContentInset` (104dp) at the
  bottom, so no content is ever trapped behind it.
- **Glass.** One translucent capsule: a background tinted from the app surface, a vertical sheen
  gradient (bright at the top edge, accent-tinted at the bottom), and one hairline rim. Content
  behind reads through at roughly 25–30% — enough to feel like glass, never enough to hurt labels.
- **Liquid.** Selection is a *single* blob driven by one `Animatable<Dp>` spring
  (`dampingRatio = .62f`, `stiffness = 340f`). The blob's squash/stretch is computed from that same
  animation's **velocity**, so it stretches along the direction of travel while in flight and
  settles back to a circle-capped pill at rest. Motion and deformation cannot desynchronise
  because they read from one source.
- **Rounded.** `RoundedCornerShape(percent = 50)` on both the pane and the blob — a true capsule at
  any height, not a fixed dp radius that breaks when the height changes.
- On AMOLED the shadow is dropped to `0.dp` (a shadow on `#000000` is only a grey smear) and the
  pane becomes a near-black translucent sheet with a brighter rim instead.

The toast host now clears the dock (`bottom = MarbleDock.Height + 22.dp` plus navigation bars).

## 2. Settings — `MARBLE_SETTINGS_INFORMATION_ARCHITECTURE_V103`

**What was wrong.** Six workspaces whose boundaries were unpredictable — DNS in *Network*,
fragmentation in *Engine*, Iran Mode in *Network* while the Freedom engine driving it was in
*Freedom*, notifications in *System* next to a debugger. Inside them, every one of ~60 switches
carried its own explanatory sentence, so each page was a wall of prose in which the actual
labels were the smallest text on screen.

**Re-cut along one question: *what am I changing?***

| Tab | Contains |
|---|---|
| **General** | Appearance, Home layout, Subscriptions, Notifications |
| **Connection** | Tunnel mode, Split tunneling, DNS, Routing |
| **Freedom** | Marble Freedom Engine, Regional protection (Iran Mode), Fragmentation & Mux |
| **Performance** | Testing & ping, Marble Intelligence |
| **Advanced** | Expert controls, Bug Finder |

Five tabs fit a phone strip without horizontal scrolling, and every card belongs to exactly one.

**Hierarchy.** Every option now has a title and a sub-title *above* it, never a caption below it:

1. workspace summary — one line, shown once in the top bar;
2. card title (`titleLarge`) + card sub-title (`bodySmall`) — the section's whole copy budget;
3. `SectionLabel` sub-heading — groups related rows inside a card;
4. the row itself — **label and state only**.

`SettingSwitch` no longer renders its `subtitle`. The parameter is kept (call sites stay valid) and
the copy is routed into the row's accessibility `contentDescription`, where it is genuinely useful.
Rows grew to `MarbleSettings.RowHeight` (58dp) with `titleMedium` labels — the small controls got
bigger, the noisy text went away.

The seven "show X on Home" switches were split out of the theme card into their own **Home layout**
card with *Status cards* / *Controls* sub-headings.

The tab strip is now one glass rail of capsules with an animated filled selection, instead of six
free-floating elevated cards each casting its own shadow.

## 3. Dark theme — `MARBLE_AMOLED_BLACK_THEME_V103`

The dark palette was navy (`#000033`). It is now a real AMOLED theme.

- Base surface is **pure `#000000`** — OLED pixels physically off.
- Depth is built from near-black steps, ~3–4% luminance apart, which is the smallest difference
  that still reads as a separate plane without a grey haze:
  `#000000` → `#08090C` (raised) → `#0E1118` (panel) → `#151A24` (strongest inset).
- Ink is `#EAF2FF`, deliberately **not** pure white: white on black smears on OLED and raises
  glare for a VPN app that is mostly opened at night.
- Accents stay on the Marble blue ramp but one step brighter, because saturated colour on black
  loses apparent brightness.
- `prismElevated` collapses elevation to `0.dp` on AMOLED and compensates with a higher-contrast
  hairline; `prismWell` becomes an opaque near-black step instead of an alpha wash that would be
  invisible over black.
- `PrismBackdrop` scales its ambient glows to 34% and switches the dot grid **off** entirely — any
  lit pixel on an otherwise black panel shows as banding in a dark room.
- Material You dark still forces the surface stack back to true black; only the accents come from
  the wallpaper.
- `values-night/styles.xml` makes the *launch* theme black too, removing the full-screen white
  cold-start flash on a device in dark mode.
- The theme picker's dark swatch previews `#000000` and is labelled **Black / AMOLED**.
