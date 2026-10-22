# V221 — Meridian: flat, fast, and harder to censor

`MARBLE_MERIDIAN_V221`

> *"A new modern flat interface in all pages and options and boxes and colours and themes; new useful
> options; new tools for breaking filtering and censoring the internet; more power from the cores,
> and every core updated; faster connection, speed, ping and jitter; find and fix design issues and
> any bugs that exist."*

That is a large request, and it resolves into five questions the product had to answer honestly:

1. What does "flat" mean when the app already has a design system?
2. Which options are *useful* rather than merely new?
3. What is a censorship *tool* that is not a button that says "bypass"?
4. What can be made faster without lying about measurements?
5. Which defects were real, and which were only noise?

This chapter is the answer to each, and every claim below is pinned by a unit test or a source
invariant that runs before a build.

---

## 1. Flat is a material, not a missing shadow

The V213→V220 passes moved the product onto Android's own roles: opaque tonal cards, a neutral
directional hairline, a platform shadow pair, a specular wash at the top edge. That was right, and
it is also *heavy*: every card carried two shadows, a vertical gradient and a radial glow, and the
settings hub carried three of those per group.

V221 adds a second material and one switch:

| | Classic (V220) | Flat (V221, default) |
|---|---|---|
| Fill | `surfaceContainerLowest` | one tonal step of the page (`Aether.Glass`) |
| Edge | directional gradient, lit at the top | one plain hairline, accent only when selected |
| Shadow | platform pair + contact shadow | none |
| Specular wash | yes | none |
| Glow | 4.5–10 % accent radial | 1.8–4 % |

The switch is `flatInterface`, and it is provided once, through
`LocalMarbleFlat` in `AetherTheme.kt`. Every material that used to decide its own depth — `PrismPanel`,
`prismElevated`, `marbleSurfaceDepth` (the Home presentations), `marbleLitSurface` (rows, tiles,
sheets) and `marbleNativeShadow` (every platform surface) — reads that one local. That is why a
single toggle changes the Home page, the Servers grid, the Settings hub, the sheets, the dock and the
dialogs together instead of only the boxes V221 happened to rebuild.

The perf consequence is real and modest: a flat card paints one fill and one border instead of two
shadows, two gradients and a radial glow. It is not a benchmark claim; it is fewer draw ops per row,
which is measurable with the platform's own GPU profiler and worth having on a mid-range phone.

## 2. Accent: one colour, every role

Every "Marble blue" in the product was one accent wearing one hue — the selected tab, the connected
ring, the slider trail, a chosen server's rim, the Settings selection wash. V221 lifts that hue into a
named palette:

`Ocean` (the V220 brand, byte-identical), `Violet`, `Emerald`, `Amber`, `Rose`, `Graphite`.

`AetherFlowTheme(accentId = …)` re-tints the palette's two brand slots plus their rim derivations, so
one preference reaches every page at once. `Ocean` returns the palette untouched — an install that
never opens the new page cannot tell the feature exists. Dynamic ("wallpaper") themes keep the
wallpaper's colours; an accent is a choice the user makes *about the app*, not about their wallpaper.

## 3. Power: a projection, not a second settings store

The product had grown twenty expert knobs (multiplex, fast open, socket buffers, handshake budgets,
measurement concurrency) and no answer to *"make it faster"*. `CorePower` is that answer, and it is a
projection with three hard rules:

* **Device-aware.** Concurrency and buffer sizes are computed from the phone's own cores and RAM;
  a 2 GB device is never handed a flagship's pool.
* **Bounded.** Every value is clamped to the range the config builders clamp to
  (`CorePowerV221Test.smallAndUnknownDevicesAreNeverOversubscribed` walks every profile × device).
* **Never identity.** No profile touches routing, DNS fail-closed behaviour, certificate policy or
  address-family policy (`tuningNeverTouchesRoutingOrAddressPolicy`).

`Balanced` is the identity function, and a test pins that too: the default install gets exactly the
settings it configured. `Turbo` and `Extreme` add multiplexing (only where the route can carry it),
TCP fast open, larger socket buffers and wider measurement pools; `Efficient` gives battery back.

## 4. The Censorship Lab: measure, name, prescribe

The product already knew a lot about censorship — ISP fingerprints, technique recognition, MTU
healing, jitter survival. What it did not have was the thing a user needs when a page will not open:
*what is blocking me right now, and which one switch gets around it?*

`CensorshipLab` splits that question in two pure halves and one measured half:

* `CensorshipProbe` runs five bounded probes on the live path (two TLS names, one literal address,
  two alternate ports) plus a resolver question, in parallel, through the product's existing
  `MultiVectorReachability` — no second network stack. It deliberately makes **no** UDP or QUIC
  claim: the app cannot measure a blocked QUIC handshake without opening its own UDP path, and a
  technique the lab cannot see is a technique it must not report.
* `CensorshipLab.diagnose` classifies the evidence into `DNS_TAMPER`, `SNI_FILTER`, `TLS_RESET`,
  `IP_BLOCK`, `PORT_BLOCK` and `THROTTLING`. The distinctions are the value: a probe
  that dies at TCP is a different block from one that completes TCP and dies inside the handshake,
  and a resolver that fails while the literal address answers is a different block again.
* `CensorshipLab.prescribe` maps the verdict onto the knobs the cores actually read, and it refuses
  to spend a protection the user switched off.

### What the prescription may touch

The pinned extended core documents two client-side handshake treatments
(`docs/configuration/shared/tls.md` at the pinned tag):

| Key | Meaning | When V221 emits it |
|---|---|---|
| `record_fragment` | split the ClientHello across TLS records | `TLS records` |
| `fragment` + `fragment_fallback_delay` | split the raw handshake across TCP segments | `Full handshake` |

`Automatic` writes **no key at all**. It is a promise about the network, not a document field: the
repository folds it into `TLS records` only when the app's own evidence (Iran mode, whose detection
is built on ISP fingerprints and technique recognition) says this path filters TLS, and into `Off`
everywhere else. That is what keeps a default install — and every configuration V220 could produce —
byte-identical on the wire until there is something to defend against. A test pins both halves.

`spoof` / `spoof_method` are **deliberately never emitted**: they need raw sockets and elevated
privileges, which an Android app cannot obtain. A config the core refuses to start is not a bypass.
REALITY is excluded from fragmentation on purpose — its ClientHello *is* the camouflage — and QUIC
transports (Hysteria 2, TUIC) have no TLS record to split.

## 5. Faster: three real changes and one honest boundary

* **Warm cache.** `ConnectWarmup` sits in front of `AddressFamilyPolicy.resolveCandidates` with a
  90-second TTL, a 64-host bound and no negative caching. `RouteProbe` consults it first and refills
  it after every resolution; the repository warms the active route on launch and again at connect,
  on its own daemon thread. The first ping and the first connection stop paying for DNS.
* **Happy Eyeballs obeys its setting.** `RouteProbe.tcpConnect` had a hard-coded 200 ms stagger while
  `happyEyeballsTryDelayMs` (default 60 ms) was read only by the core's dialer. On a censored path,
  where the first family is often the blocked one, that constant was a visible pause on every
  connect.
* **Fewer draw ops per surface.** See §1.

The boundary: none of this changes a timeout on a real network probe, weakens certificate
verification, DNS fail-closed behaviour or route identity, and none of it is presented as a measured
throughput number. Device-only UI, VPN permission flows and carrier-specific performance still
require Android device testing.

## 6. Bugs fixed in this pass

* **Locale-dependent formatting.** Seven byte/speed formatters in `Aether2026.kt`, one in
  `MarbleVpnService.kt` and every `String.format` in `AdaptiveMtuPolicy`, `ProfileFlapGuard`,
  `SurvivalFirstRanker` and `TcpStressMonitor` used the device locale, so a Persian or German phone
  rendered `12٫5 ms` / `12,5 ms` in English UI and machine-readable strings such as
  `armed:0,37/min`. All are pinned to `Locale.US`.
* **A warm cache cannot resurrect a dead address.** Entries expire, a clock that moves backwards
  drops the entry, and a failed resolution is never stored (`MeridianV221Test`).
* **A prescription cannot override an explicit `Off`.** If the user switched fragmentation off by
  hand, the lab says so and leaves it off.

## 7. What did *not* change

The identity guard, the encrypted-DNS fail-closed contract, the address-family ladder, the routing
truth, the one-ping-verb-per-surface rule, the measurement table and every V213–V220 invariant are
untouched and still enforced by `scripts/system-integrity-check.py` (352 checks before this pass,
plus the V221 group).

## Verification

* `scripts/system-integrity-check.py` — all checks pass, including the new V221 group.
* `app/src/test/java/com/marbleng/app/core/{CorePowerV221Test,CensorshipLabV221Test,MeridianV221Test}.kt`
  — 28 cases, all pure JVM.
* `:app:testDebugUnitTest` and `:app:compileDebugKotlin` / `:app:compileReleaseKotlin` in the source
  verification workflow are the compile gate.
