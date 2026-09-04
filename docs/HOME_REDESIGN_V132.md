# MarbleNG — Home redesign (V132)

The Home page was rebuilt. The evidence model did not change — `HomeEvidence` is still the one
snapshot every presentation renders — but the arrangement, the chrome and the connection
controls are new.

## The new Signature Home, top to bottom

```
┌───────────────────────────────────────────────┐
│ 1  SELECTED SERVER                            │  ← the only server on the page
│    🇩🇪 Hamburg-03            [VLESS]   Change  │
│    Group 1                                    │
├───────────────────────────────────────────────┤
│ 2  ╭───────────╮   ┌──────────────┐           │
│    │  connect  │   │ ● LIVE PING  │           │  ← opens with an animation
│    │  control  │   │    118 ms    │           │
│    ╰───────────╯   └──────────────┘           │
│         PROTECTED                             │
├───────────────────────────────────────────────┤
│ 3  [ + ] [paste] [QR] [ ⌁ Ping   118 ms ]     │  ← ping is always present
├───────────────────────────────────────────────┤
│ 4  ● Protected                       118 ms   │  ← the status banner, moved down
│    Hamburg-03                                 │
├───────────────────────────────────────────────┤
│ 5  Server / Source · IP + flag · Uptime/Ping  │
└───────────────────────────────────────────────┘
```

### 1. Only the selected server (`HomeSelectedRouteCard`)

Home is not a second Servers page. It renders **exactly one** route: the one the user selected
on the Servers tab (the live route wins while a tunnel carries traffic). The card shows the
flag, the node name, the **group / subscription it was chosen from**, and its protocol. Tapping
it opens Servers — the single, unambiguous way to change the selection.

So "group 1 of 10 groups" is a fact on screen, not a menu.

### 2. The connect stage (`HomePowerStage`) with the live ping instrument

`HomePowerStage` wraps the chosen connection control and its live ping instrument in one block:

* the instrument opens the moment the route starts moving and closes the moment it stops;
* it lives in a **reserved slot**, so the control itself never shifts when the panel appears;
* on wide layouts (landscape, tablets, ≥ 640 dp) the pair sits **side by side**; on a portrait
  phone the instrument opens directly **above** the control;
* the Signature hero switched from a fixed height to `heightIn(min = …)`, so the reveal is the
  hero growing — nothing is ever clipped or pushed off screen.

### 3. The live ping meter (`HomeLivePingMeter`)

A compact latency instrument:

* an **arc gauge** (0–400 ms full scale) tinted by the product's own quality bands;
* the **current value** in the middle, three dots while a probe is in flight, an em dash before
  the first measurement;
* a **sparkline** of the last 24 probes, so a single spike reads as context rather than failure;
* the name of the **one server** being measured.

It measures **only the connected server**. The repository owns a one-shot probe with no
background timer; the meter is the only place allowed to re-arm it, and it does so only while it
is on screen and the tunnel is genuinely up (`LIVE_PING_INTERVAL_MS = 3_000`). Close the panel
or drop the route and the ladder stops immediately.

The two classic presentations (`Cosmic orbit`, `Cosmic immersion`) host the same instrument as
a free-standing row under their control through `HomeLivePingSlab`, so every Home presentation
answers a connect with the same reveal.

### 4. The shortcut deck (`HomeShortcutDeck`)

`+` (add server), **paste** (smart clipboard intake), **QR** (camera or gallery), and **ping**.

* Paste and QR use exactly the same smart intake as the Servers page
  (`AppRepository.importClipboard` / `importQrImage` / `importQrBitmap`), so a pasted
  subscription still becomes a real subscription instead of a bogus proxy profile.
* **Ping is a permanent member of the deck.** It is not conditional on a measurement having
  landed, it always shows the current value when one exists, and it always says what a tap will
  do.

### 5. The status banner moved down

The banner is a *reporting* strip; the four entries above it are *actions*. Actions now sit
above the report so they are never pushed off the first screen by a strip that only states the
obvious.

---

## Five connection controls

`ConnectButtonStyle` gained two bottom-docked silhouettes. All five are selectable from
Settings → Appearance → Connect button, and all five render in every Home presentation.

| Style | Placement | Language |
| --- | --- | --- |
| `ROUND` (default) | centred in the hero | the large round shutter |
| `SLIDE` | hero floor | drag the knob left → right, like a safety switch |
| `CLASSIC` | power dock under the hero | the classic rectangular power switch |
| `STREAM` | **floor of the page** | full-width bar with a light band travelling **right → left** |
| `FLOATING` | **docked above the bottom edge** | compact pill, v2rayNG-style but pinned |

### `STREAM` — the travelling band

The band enters from the **right** edge and leaves through the **left** edge, continuously. It
is screen-space motion pinned to LTR, exactly like the slide knob, so Persian and English see
the same direction. It is bright and quick (1.6 s period) while the tunnel is opening or
closing, slow and quiet (3.4 s) while the route is simply protected, and slowest (5.6 s) when
idle.

Because it is docked at the floor of the page it is visible at every scroll position, and the
hero becomes a quiet state plate over the same artwork.

### `FLOATING` — the docked pill

The user's suggestion of a floating button is honoured as a **docked** pill rather than a
draggable overlay: it cannot cover a readout behind it, and it is always where the thumb
expects it.

### Placement is now a pure, tested mapping

`ConnectControlZone` and `connectControlZone()` moved into `ui/MarbleConnectPlacement.kt` — no
Compose, no Android — so the "two silhouettes must never compete for the same spot" contract is
covered by unit tests in `MarbleHomeStyleTest`.

---

## Bottom dock: the night-mode white flash

`FloatingSpatialDock` painted its glass sheen with

```kotlin
Aether.BarGlassHighlight.copy(alpha = highlightAlpha)
```

`copy(alpha = …)` **replaces** the token's own alpha with the raw 0…1 animation value. In the
AMOLED palette `BarGlassHighlight` is `Brand.Ice (#ADD8E6)` at `alpha = .08`, so the moment a
tab was tapped — a tap sets `pagerState.isScrollInProgress`, which switches the glass state on —
the bar was painted with a **full-opacity ice-blue band** across its top. That is the "the bar
turns white when I tap it in night/AMOLED mode" bug. The sheen now scales the token's own alpha,
exactly like the surface and the border already did.

The same revision removed every ambient transform from the dock:

* the whole bar no longer breathes up and down on the shared frame clock
  (`MARBLE_DOCK_IDLE_BREATHE_V131` is gone — it read as a wobble under the thumb);
* the bar no longer shrinks by 1.5 % during a page turn;
* the selected pill no longer pulsates.

Only colour, shadow depth and the selection wash animate, and all of them now use the
overshoot-free `MarbleMotionSpecs.DockFloat` / `DockDp` tweens (180 ms) instead of springs, so
nothing can overshoot and flash.

---

## Files

| File | Role |
| --- | --- |
| `ui/MarbleHomeStudio.kt` | **new** — selected-route card, live ping meter, shortcut deck, stream bar, floating pill, floor dock |
| `ui/MarbleConnectPlacement.kt` | **new** — pure placement mapping for the five silhouettes |
| `ui/MarbleSignatureHome.kt` | Signature Home rebuilt in the new order |
| `ui/MarbleHomeStyles.kt` | two new silhouettes dispatched from `MarbleConnectionButton`, live-ping slabs in the classic styles, `PASTE`/`QR` glyphs |
| `ui/Aether2026.kt` | still dock, paste/QR launchers, Settings motifs for the new styles |
| `ui/MarbleMotion.kt` | `DockFloat` / `DockDp` overshoot-free tweens |
| `ui/MarbleStrings.kt` | new product strings, English + Persian |
| `ui/MarblePersianLexicon.kt` | Persian for the new Settings literals |
