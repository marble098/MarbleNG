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

## Live telemetry (implemented)

The orb's **ping / down / up** are real, not placeholders:

- **Throughput** — `MarbleVpnService.startTelemetry()` samples the native HEV
  byte counters (`HevTunnel.stats()` → `[txPackets, txBytes, rxPackets,
  rxBytes]`) once per second and computes per-second deltas. `txBytes` maps to
  **upload**, `rxBytes` to **download** (hev-socks5-tunnel convention — swap
  `s[1]`/`s[3]` if your build reports them inverted).
- **Ping** — every ~4 s the service opens a SOCKS5 `CONNECT` handshake to
  `www.gstatic.com:443` through the local proxy and times the reply. Because
  the service adds `addDisallowedApplication(packageName)`, the app's own
  sockets bypass the TUN, so this measures true egress RTT through Xray.
- Values land in observable `AppRepository` state (`liveDownBps`, `liveUpBps`,
  `livePingMs`); `toProxyState()` reads them, so the orb recomposes ~1×/s while
  connected and resets to idle on disconnect/block.

## Accurate ping (fixed)

The earlier probe timed only the SOCKS5 CONNECT reply, which Xray answers
locally *before* dialing upstream — so it read a meaningless 2–4 ms. `LatencyProbe`
now completes the tunnel: after the SOCKS handshake it sends a real HTTP request
to `www.gstatic.com/generate_204` (plain HTTP, no TLS to skew the figure) and
times the first response byte. That interval includes the true outbound dial +
server round-trip. A single shared ticker in `AppRepository` runs it every 3 s
through the active port and works in **both** connection modes.

## Two connection modes (main deck)

A segmented control on the deck picks how traffic is captured:

- **Full Tunnel (VPN)** — the original device-wide TUN via HEV + Xray.
- **Local Proxy** — starts Xray only, exposing a SOCKS listener on
  `127.0.0.1:10101` (port adjustable in Settings) for the user to point chosen
  apps/browser at. No VPN permission, no TUN. Throughput counters aren't
  available here (no TUN), so the orb shows ping + the SOCKS address instead of
  down/up. Implemented via `AppRepository.startLocalProxy / stopLocalProxy /
  autoProxy`; the toggle is locked while a tunnel is live.

## Smart routing + geo databases (Settings)

`XrayConfigHardener` stays fail-closed by default, but when the user enables
**Smart routing** it accepts a `RoutingSpec` and builds a real routing table:

- **geoip.dat / geosite.dat** — the app bundles none, so Settings lets the user
  paste URLs (default: Loyalsoldier rules-dat) and downloads them **once** into
  Xray's asset dir (`AppRepository.downloadGeoAssets`, streamed to a `.part`
  temp then renamed). `routingSpec()` guards against missing databases by
  dropping `geoip:`/`geosite:` matchers until they're installed, so a partial
  setup never breaks `xray -test`.
- **Full management** — domain strategy (AsIs / IPIfNonMatch / IPOnDemand),
  quick toggles (bypass LAN via `geoip:private`, block ads via
  `geosite:category-ads-all`), and unlimited custom rules (name + action
  PROXY/DIRECT/BLOCK + domain & IP/CIDR matchers), persisted in `AppStore`. A
  single `direct` freedom outbound is added only when a rule actually needs it,
  and `verify()` permits it solely under that explicit opt-in.

## Cross-page consistency

Every screen (Library, Lab, Radar, Settings) now shares the same Aether Flow
language via upgraded primitives in `MarbleApp.kt`: `Header`, `SectionLabel`,
`GlassCard` (true frosted glass + hairline border), and `ActionGrid`/
`AetherPill`. Remaining cryptic tags were decoded in place — theme chips
`AUR/OCE/SUN` → `Aurora/Ocean/Sunset`, and benchmark modes `REL/BAL/FAS` →
`Reliability / Balanced / Maximum Speed` via `benchModeLabel()`.
