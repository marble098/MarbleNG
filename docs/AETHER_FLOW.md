# 🌌 Aether Flow — Main Control Deck Blueprint

A ground-up redesign of the MarbleNG control surface. The philosophy: a proxy
app should feel like a fluid, invisible, advanced **network nexus**, not a
developer debug console. We achieve this with deep OLED black (`#050505`),
frosted glassmorphism, a context-aware amethyst→cyan accent that *breathes*
while connected, and relentless **progressive disclosure**.

## Structural anatomy of the Deck

```
┌─────────────────────────────────────────────┐
│  TOP BAR                                      │
│  "Aether Flow" + live status line   [pill][⚙] │  ← identity + at-a-glance state
├─────────────────────────────────────────────┤
│                                               │
│              ╭───────────────╮                │
│              │   THE  ORB    │                │  ← Hero / Nexus
│              │   ⚡ CONNECTED │                │    breathing conic gradient,
│              │   Amsterdam   │                │    tap → ripple → best node,
│              │  ping down up │                │    telemetry fades in when live
│              ╰───────────────╯                │
│                                               │
├─────────────────────────────────────────────┤
│  SMART ACTION ROW  (exactly three)            │
│  [🧭 Smart Route] [🇳🇱 Node Library] [🛡 Shield]│  ← contextual, one job each
├─────────────────────────────────────────────┤
│  ROUTING INTELLIGENCE  (horizontal cards)     │
│  [Reliability][Balanced][Max Speed][Turbo]    │  ← REL/BAL/FAS decoded
└─────────────────────────────────────────────┘
        ▲ swipe up / tap ⚙
┌─────────────────────────────────────────────┐
│  ENGINE ROOM  (ModalBottomSheet)              │  ← the whole developer sandbox,
│  Observability · Integrity · Maintenance      │    hidden until summoned
└─────────────────────────────────────────────┘
```

### 1. Top Bar
Brand wordmark + a single-line, live status caption. A **status pill**
(`SECURED / LINKING / BLOCKED / OFFLINE`) gives instant state without reading
any body text. The `⚙` handle opens the Engine Room.

### 2. The Hero — Connection Orb (`ConnectionHero`)
The rectangular connect buttons are gone. In their place: one massive circular
nexus that *is* the app's state.

- **Idle** → muted slate, static, `⏻ TAP TO CONNECT`.
- **Connecting** → amethyst, fast-spinning conic ring, `···`.
- **Connected** → amethyst→cyan gradient that **breathes** (infinite pulse),
  and live telemetry (**ping / down / up**) fades in beneath the glyph.
- **Blocked** → rose/danger accent, `⚠ TAP TO RETRY`.

Tapping fires a ripple ring and either connects to the best node (`repo.auto`)
or tears the tunnel down.

### 3. Smart Action Row (`QuickActions`)
Only three cards ever appear — the 80% of what users actually want:
**Smart Route** (auto-benchmark & connect), **Node Library** (opens the
library, badged with the live node count / region flag), and **Privacy
Shield** (leak audit). Everything else is disclosure.

### 4. Routing Intelligence (`RoutingIntelligence`)
The cryptic `REL / BAL / FAS` tags become illustrated selector cards with
human titles and micro-descriptions:

| Old | New | Micro-description |
|-----|-----|-------------------|
| `REL` | 🛡 **Reliability Mode** | Locks onto the most stable, long-lived nodes |
| `BAL` | ⚖ **Balanced** | The optimal blend of speed and stability |
| `FAS` | ⚡ **Maximum Speed** | Chases the absolute lowest-latency egress |
| `TUR` | 🚀 **Turbo Burst** | Aggressive parallel probing for peak throughput |

### 5. Engine Room (`DiagnosticsBottomSheet`)
Everything that made the old UI a "debug nightmare" — Logs, History, System
Doctor, Capabilities, Core Lock, Core Update, Xray JSON import — is swept into
a single `ModalBottomSheet` grouped into **Observability / Integrity /
Maintenance**. Regular users never see it unless they summon it.

## State model

The entire deck is a pure function of one immutable snapshot,
`ProxyState` (see `AetherDeck.kt`), projected from the live `AppRepository`
via `AppRepository.toProxyState()`. This keeps every composable
(`ConnectionHero`, `QuickActions`, `RoutingIntelligence`,
`DiagnosticsBottomSheet`) isolated, testable, and `@Preview`-able through
`ProxyState.PreviewConnected`.

## Design tokens & theme

All colours and the geometric type ramp live in `AetherTheme.kt` (`Aether`
object + `AetherFlowTheme`, an MD3 `MaterialTheme` wrapper). To adopt the
exact "Aether Flow" voice, drop **Inter** or **Manrope** into `res/font/` and
point `AetherFontFamily` at it — the weights and tracking are already tuned.

## Telemetry note

`ProxyState` carries `pingMs`, `downloadKbps`, and `uploadKbps`. Ping is
derived from the last benchmark for the connected node. Real-time up/down
throughput fields are wired and ready — feed them from a live tunnel-stats
source (e.g. HEV byte counters surfaced through `MarbleVpnService`) and the
orbiting telemetry animates automatically.
