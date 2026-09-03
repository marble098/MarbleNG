# Servers, the Settings hub and the fixed ping — V114

`MARBLE_SERVERS_V114`, `MARBLE_SETTINGS_HUB_V114`, `MARBLE_PING_GUARANTEE_V114`,
`MARBLE_PING_FIXED_GEOMETRY_V114`, `MARBLE_SEAMLESS_LOOPS_V114`, `MARBLE_HOME_GLAMOUR_V114`,
`MARBLE_SETTINGS_QUIET_CHROME_V114`, `MARBLE_VAZIR_LANGUAGE_KEY_V114`,
`MARBLE_INFORMATION_PAGE_V114`, `MARBLE_SERVERS_LANGUAGE_V114`

Seventeen product requests, one round. Nothing here adds a permission, a network call or a
background worker; the work is presentation, one measurement rewrite and one rename.

---

## 1. "Node" is now "server", "Library" is now "Servers" (both languages)

The rename is user-visible only. Enum ids, persisted keys and function names keep their old
identifiers so a stored `homeStyle`, `proShortcut = library` or a collapsed-source id still
resolves after the upgrade:

- `SpatialTab.LIBRARY`, `ProShortcut.LIBRARY`, `HomeIcon.LIBRARY`, `NodeSortMode`,
  `libraryProfiles`, `libraryCollapsedSources` — unchanged identifiers.
- Every string the user reads changed: `tabLibrary` → "Servers" / "سرورها",
  `node` → "Server" / "سرور", the five screen crowns → "SIGNATURE SERVERS", "ORGANIC SERVERS",
  "ORBITAL MANIFEST", "NEBULA SERVERS", "BLUEPRINT SERVERS".
- Persian is semantic, not literal: **نود → سرور** and **کتابخانه → سرورها** across
  `MarbleStrings` and the 680-key `FaLexicon`, including the compound patterns
  (`12 servers` → `12 سرور`, `Freedom (12)` → `فریدم (12)`, `9 shown` → `9 نمایان`).
- `tools/rename-servers-v114.py` did the mechanical pass. It is literal-only: it skips comments,
  `${…}` interpolations (including multi-line ones), `kebab-case`, `camelCase` and `SCREAMING_CASE`
  literals, so no persisted key or CSS-like id was touched.

`scripts/system-integrity-check.py` follows the wording it guards: the disconnected-only deletion
guard is now asserted as `"Disconnect before deleting servers"`.

## 2. The ping can no longer say "no response" (`AppRepository.measureConnectionPing`)

The old probe had two failure modes the user experienced as a broken product: it took up to nine
seconds, and a blocked HTTPS origin made a healthy tunnel report failure.

The rewrite keeps the V112 idea (a race of independent, provider-diverse measurements through the
live Xray path) and adds three guarantees:

1. **Instant.** The tunnel monitor already measures live route RTT continuously; the first frame
   after a tap shows that real number (or the stored benchmark of the connected server), so the
   readout never sits on a placeholder. The first *verified* race sample is published the moment it
   lands, and the fastest sample of the whole race refines it afterwards.
2. **Bounded.** Per-probe socket timeouts are 1.6–2.0 s and the pool is invoked with a 2.6 s budget
   (`invokeAll(…, 2_600, MILLISECONDS)`), then shut down. It was 9 s.
3. **Never empty.** The ladder is: fastest verified HTTPS sample (IP-literal TLS first-byte ×4 with
   real SNI hostnames, `generate_204` ×3, one full GET) → fastest unverified sample (a SOCKS
   `CONNECT` handshake through the same tunnel, which answers even when every HTTPS origin is
   blocked) → live monitor RTT → stored benchmark of the connected server.
   `ConnectionPingState.FAILED` is now reachable only when no rung has a number, which cannot happen
   while `state == "CONNECTED"`.

A disconnect or reconnect during the probe still invalidates the result (`connectedSinceMs`
comparison), and the probe is still one-shot — never a timer, so it adds no traffic to a metered
connection. Every outcome is reported to diagnostics with the winning mode.

## 3. The ping box is an instrument with a fixed geometry

`HomeStatMetrics` (`MarbleHomeStyles.kt`) is the measured contract of a style's two instruments:

```
cellHeight · headerSlot · valueSlot · meterSlot · hintSlot · spacing · dialSize
```

- Both cells of a pair are `Modifier.weight(1f).height(metrics.cellHeight)` — **identical width and
  height** in every state and every language.
- Slot heights come from `anchoredTextBlockHeight(style, 1)` through the current density, so
  accessibility font scaling still fits instead of clipping.
- `HomeStatValueText` no longer auto-shrinks by default (`autoFit` is opt-in) and renders tabular
  digits with a locked line box, so `9 ms → 10 ms` does not shift the glyphs beside it.
- State **words** moved out of the value slot: `homePingLabel` only ever emits digit-shaped glyphs
  (`123 ms`, `•••`, `—`) and `HomeStatHintSlot` reserves the one line underneath for
  "measuring…" / "test again" — on **both** instruments, so a hint appearing on the ping can never
  make it taller than the uptime beside it.

## 4. Seamless loops in all five styles

The rule is now mechanical: an element that wraps must complete a **whole** number of journeys per
loop, and must be invisible at the wrap point.

| Style | Was | Now |
| --- | --- | --- |
| Bioluminescent god-rays | `pan = (seed + drift * .18f) % 1f` — snapped back 18 % of the width at full opacity | one full crossing per 46 s loop, `loopFade` at both ends |
| Bioluminescent plankton | fractional per-mote speed — every mote jumped to a random height on wrap | integer journeys per mote (1–3) on a 19 s loop |
| Cosmic Orbit planets | `angle = ((phase * (1 + i * .55f)) % 1f) * 2π` | one full turn per orbit on coprime periods (14/23/37 s) |
| Nebula parallax stars | fractional per-layer drift | one full crossing per layer, parallax by *period* (126/84/58 s) |
| Nebula sky orbits | `phase * (1 + i * .4f)` | one full turn per orbit (21/34/55 s) |
| Signature motes | fractional speed | integer journeys per mote |

Already-seamless work was left alone: `sin`/`cos` oscillations, `* 360f` rotations, full-2π comets,
the five-slot symmetry of the 12-petal flower and every `loopFade` scan.

## 5. More glamour, still five different products

Canvas-only additions, no bitmaps and no new infinite transitions:

- **Bioluminescent** — surface caustics (two interfering sine sheets), a depth vignette, a sheen
  that crosses each stat cell while a probe runs.
- **Cosmic Orbit** — diffraction crosses on the brightest stars, a warm deck horizon, a vignette,
  a radar sweep and travelling needle on the ping gauge.
- **Cosmic Immersion** — one long shooting star per orbit loop (fully faded before the wrap),
  starfield specks twinkling inside the stat rings, an aurora band that settles to the measurement.
- **Parametric** — a corner lamp, margin rules and a paper vignette on the drafting sheet, an
  end-tick on the latency dimension bar.
- **Signature** — a studio light band and vignette behind the modules, specular edges, a breathing
  live dot, a graded latency underlay on the ping cell.

## 6. The Servers screen: one frosted module per source

`CyberLibrary` no longer paints a card per server. Each source is **one container**:

- `LibraryModuleSegment` pours the same frost (translucent gradient + hairline edges + inset
  divider) across every lazy item that belongs to the module — header, rows, tail. The list is
  still itemised per row, so a 500-server source never composes in one frame.
- `LazyColumn` has no blanket `spacedBy` any more; blanket gaps would have cut the module into
  slices. Each item owns the space above and below it.
- **Folding is animated**: rows shrink and fade in a short stagger (`expandVertically` /
  `shrinkVertically` + `fadeIn`/`fadeOut` with a per-index delay) and the tail folds with them.
  A folded module keeps eight rows composed — more than a viewport holds — so nothing on screen
  ever pops out of existence.
- **Nothing about a source is hidden any more**: the name wraps to two lines, and the server count,
  the visible-while-searching count (`12 shown` of `340 servers`) and the time since the source was
  added each get their own chip in a wrapping `FlowRow`.
- Rows are 54–60 dp tall, back-to-back, and each style draws them differently:
  Signature studio lines with track numbering and a marquee, bioluminescent spore rows with a
  breathing glow, cockpit manifest entries with dotted leaders, nebula capsule rows with a ring
  gauge, drafting spec lines with a dimension rule under the latency.
- Latency is spoken as well as painted: `"Latency 148 milliseconds, good"` for TalkBack, in a slot
  that never changes size.

### The floating magic button

`LibrarySmartFab` follows three rules: a single tap opens **nothing** (the old add-page shortcut is
gone), *every* action floats **above** the button and appears **only** while the finger is held, and
the button is a **clipboard** (new `HomeIcon.CLIPBOARD`, drawn on the same vector canvas as every
other glyph). A dimmed field behind the stack gives the hold an obvious way out, and a "Hold for
actions" caption shows until the button has been used once.

## 7. Settings is one hub page

`SpatialSettings` is now a navigator over string page keys (`rememberSaveable`, so it survives
process death) with `AnimatedContent` slide+fade in the direction of the hierarchy and a
`BackHandler` that always walks up to the hub:

```
hub ─┬─ workspace:GENERAL|FREEDOM|TESTS|NETWORK|ENGINE|SYSTEM   (the classic tab workspace)
     ├─ theme · home-style · typeface · language · information
```

- **Hub** — three hierarchical groups (Connection / Appearance / System) of titled rows, each row
  carrying a *small live preview* of what it controls: a routing line, fragment shapes, a
  measurement ladder, theme dots, the style's own miniature artwork, "Aa" in the candidate face,
  `EN | فارسی`, the version stamp.
- **Important settings stay on the hub** and apply instantly: the four theme swatches (including
  *Dynamic phone*, whose swatch paints the live Material You palette), the five Home-style
  thumbnails, plus reconnect-last-server, auto-refresh sources, smart alerts, Marble Intelligence
  and expert mode.
- **Sub-pages** — Theme (full-size previews + night outlines + studio accent), Home style (five
  thumbnails drawn from each style's artwork), Typeface (each candidate rendered in its own face),
  Language, and Information.
- **Information** — `BuildConfig.VERSION_NAME` / `VERSION_CODE` / `BUILD_TYPE`, the pinned cores
  (`XRAY_CORE_TAG`, `HEV_CORE_TAG`) and their repositories, compiled in from `core-lock.json` by
  `app/build.gradle.kts` so the numbers on screen cannot drift from the binaries in the APK. Every
  link (source, releases, issues, both cores) opens the **browser directly** via `ACTION_VIEW`.
- **Thinner type** — the settings ramp is its own: titles Medium at `titleSmall`/`labelLarge`,
  body Normal at `labelSmall`. Nothing in Settings is Bold.
- **No status bars** — the 3 dp gradient bar beside every `SectionLabel` and the marker dot beside
  every `FreedomSectionHeader` are gone. Hierarchy is carried by type alone.
- The workspace itself is untouched where it matters: one ordinary `LazyColumn` with the tab strip
  as a `stickyHeader` on mobile, a `NavigationRail` + direct `LazyColumn` at `>= 700.dp`, one
  `imePadding()` pass and no `BoxWithConstraints` — it only gained a way back to the hub.

## 8. The Persian language key is always Vazir

`VazirFamily` is `internal` (was `private`) and `CyberSegment` gained `labelFontFamily` +
`rawLabel`. The Persian choice is written in Persian (`فارسی`), never routed through the translator,
and rendered in the bundled Vazirmatn TTF in every state — idle, selected, pressed, and under an
English UI. The Language page and its hub preview do the same.

---

## Verification

- `python3 scripts/system-integrity-check.py` → **126/126**.
- Every Python invariant block in `.github/workflows/verify.yml` passes locally.
- `python3 tools/kotlin-structure-check.py <file>` (new) tokenises Kotlin — string/char literals,
  comments, `${}` interpolations — and asserts brace/paren/bracket balance. All 93 `.kt` files pass.
- `python3 tools/compose-scope-check.py` (new) catches the failure mode a structure check cannot:
  every `Aether.*` token is a `@Composable get()` and `trx()` is `@Composable`, so reading one
  inside a `Canvas {}` draw scope, a `LaunchedEffect {}` body, a `remember {}` factory or a gesture
  callback is "@Composable invocations can only happen from the context of a @Composable function".
  It classifies every brace scope by its owning call, looks through inline lambdas (`let`/`run`/
  `apply`/`with`) that carry the context, and reports violations — 0 across the whole source set.
- GitHub Actions remains the compiler: the sandbox has no JDK, no Android SDK and no network.
