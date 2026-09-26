# V193 — Smooth motion, one core-options page, a louder fourth tab, and the flag identity

Four product fixes in one chapter: the ambient-motion anti-jank pass, the settings-page
de-duplication, the fourth tab's feature upgrade, and the server-circle identity change.

---

## 1. MARBLE_SMOOTH_CLOCK_V193 — animations and transitions stop lagging

The app already had the right architecture: **one shared frame clock** (`MarbleMotionState`) for
every ambient effect, and draw-phase reads in most of the UI. Two things still made the whole app
feel laggy, and both are fixed at the root.

### The clock delivered 90–120 fps of recomposition on high-refresh panels

`ProvideMarbleMotion` wrote its elapsed-second state on **every** vsync. On a 90/120 Hz phone
that is 90–120 state writes per second, each one invalidating every ambient reader on screen —
double or quadruple the recomposition work for motion the eye cannot separate from 60.

- The loop now delivers ambient frames at a **60 fps cap** (`AMBIENT_FPS_CAP`); faster panels fold
  the extra frames in.
- A new **coarse twin** of the clock (`coarseLoop` / `coarseBreathe`, ~15 Hz) serves the very
  large, very slow surfaces. `PrismBackdrop` — the full-screen page backdrop whose glows breathe
  on 11 s and 15 s waves — now reads the coarse clock, so the one canvas behind every page
  invalidates ~15 times a second instead of 60–120. Visually identical; it no longer competes
  with scrolling content for the GPU.

### Five controls read the clock in composition

A state read inside a composable recomposes that composable **every frame**. Five prominent
controls did exactly that, continuously:

| Control | Was recomposing per frame… |
| --- | --- |
| `ConnectButtonStream` (Home) | always — the travelling band's `phase` |
| `ConnectButtonFloating` (Home) | while connected — halo `breathe` + busy `sweep` |
| round & wide connect buttons (`MarbleHomeStyles`) | connected/busy — `sweep`, `busyPulse`, `haloPulse` |
| `ConnectionCore` (Home) | while connecting — `orbPhase` |
| `LiveProgressBar` / `PrismRouteFrame` / `PrismConnectionStage` / slide-button shimmer / Iran-mode pill | whenever visible |

All of them now read their phases **inside the draw/layer lambda** (`Canvas { }`,
`graphicsLayer { }`), which re-runs without recomposition. A connected Home no longer recomposes
anything for ambient motion; it only redraws a small canvas.

### The servers list kept permanent marquees

Every overflowing server name ran `basicMarquee(iterations = Int.MAX_VALUE)` — a permanent
frame invalidation per overflowing row, for as long as the row stayed composed. Three passes now
spell the name out and the row goes still.

---

## 2. MARBLE_SETTINGS_DEDUP_V193 — one control per concept

The Engine workspace carried the same option twice, once per core, in two different cards:

| Duplicated control | Xray card | sing-box card | Now |
| --- | --- | --- | --- |
| Sniffing | `xraySniffingEnabled` | `singBoxSniffEnabled` | one switch, follows the selected engine |
| Allow LAN inbound | `xrayAllowLan` | `singBoxAllowLan` | one switch |
| HTTP inbound port | `xrayHttpInboundPort` | `singBoxHttpInboundPort` | one field |
| Log level | `xrayLogLevel` | `singBoxLogLevel` | one picker (values adapt per core) |
| TCP Fast Open | `tcpFastOpenEnabled` | **the same shared field twice** | one switch, labelled "applies to both cores" |

The two cards became one **Core options** card that adapts to whichever engine is selected in the
Engine card above it, keeping each core's genuinely unique options (Xray: route-only sniffing,
max-config-compatibility, unencrypted-dial consent; sing-box: resolve destination, link parser,
unified delay, cache file, connect timeout).

The dead `IntelligenceSettings` composable — an unreachable 260-line duplicate of Turbo,
fragment/mux and recovery controls — is deleted outright.

---

## 3. MARBLE_DOCK_SLOT_PLUS_V193 — the fourth tab earns its bar slot

- **Pulse → Route quality (new card):** the live route's score as a settled meter, its stability
  word, jitter and a suspected-reset flag — one glance, no arithmetic.
- **Pulse → Live card:** gained the primary connect/disconnect action (the same toggle Home's
  control runs), so the slot can open and close the tunnel on its own.
- **Pulse → Metrics:** a third row — uptime and this session's data — completing the six-tile
  readout (the static caption line is gone).
- **Pulse → Tools:** reorganized into two captioned groups — **Library** (rank / ping / refresh)
  and **Diagnostics** (Bug Finder / privacy audit / IP details) — instead of one flat grid.
- **Source:** the header now states the list's truth (servers • measured • best ms and its name)
  and offers **Connect fastest** — the same select/reconnect rule every row follows, disabled
  while a ping sweep runs.

---

## 4. MARBLE_PROTOCOL_TEXT_IDENTITY_V193 — the flag is the circle; the type is text

The hand-drawn glyph set (the VLESS "V", the VMESS envelope, the sock, the shields) is removed.
At tile size it read as ugly, unexplainable shapes inside the server circles.

- `ProtocolTile` — the circle now always carries the **country**: the real national flag drawn
  edge-to-edge by `CountryFlagCircle` when the tested location is known, and the name's own flag
  glyph (or a globe) at full size while it is not. The protocol's hue survives as the resting rim.
- `ProtocolBadge` — the protocol is **text**: a pill with the label (`VLESS`, `VMESS`, `TROJAN`,
  `SS`, `HY2`, `WG`, `SSH`, `SOCKS`, `HTTP`, `PROXY`) in a hue that belongs to it alone.
- `protocolTone` — ten families, ten distinct hand-picked hues, legible on light and dark:
  violet `#7C5CFF`, sky `#0EA5E9`, emerald `#10B981`, amber `#F59E0B`, rose `#F43F5E`,
  teal `#14B8A6`, indigo `#6366F1`, magenta `#EC4899`, orange `#F97316`, slate `#94A3B8`.
- `CountryFlagCircle` gained `styleOverride` so the stand-in glyph scales with its tile.
- The never-rendered `MarbleServerAvatar` (which fell back to drawing the scheme's first letter)
  is deleted.
