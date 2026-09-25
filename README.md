# MarbleNG

<!-- MARBLENG_TELEGRAM_COMMUNITY_V46:START -->
<div align="center">

## 💎 Welcome to the MarbleNG Community

**🐞 Found a bug? &nbsp; 💡 Have a suggestion? &nbsp; 🚀 Want to help MarbleNG improve?**

The official Telegram channel is the **fastest and best place** to report problems, share ideas, follow important updates, and help shape the future of MarbleNG.

<a href="https://t.me/MarbleNG">
  <img src="https://img.shields.io/badge/Telegram-Join_%40MarbleNG-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" alt="Join @MarbleNG on Telegram">
</a>

### ✨ [Join @MarbleNG on Telegram](https://t.me/MarbleNG) ✨

<sub>📣 Bug reports • Feature requests • Suggestions • News • Release updates</sub>

</div>

<!-- MARBLENG_TELEGRAM_COMMUNITY_V46:END -->

---

**MarbleNG is a modern Android network client built around Xray-core, Android `VpnService`, and `hev-socks5-tunnel`.**

It focuses on real proxy verification, fast one-tap connection, fail-closed routing, adaptive network intelligence, and a clean Compose interface.

## Highlights

- Always remembers the **last successfully connected node**.
- One-tap Home reconnect after app restart or process death.
- Full-device Android TUN mode or local SOCKS proxy mode.
- Fast TCP reachability tests and real Xray tunnel verification.
- Smart ranking for the whole Servers list or one selected group.
- Navy/ice/electric-blue brand identity shared by the Solid White, Dark, and System themes.
- Font-independent Canvas vector icons for critical actions.
- Smart GitHub Release update checks.
- Signed multi-ABI APKs built by GitHub Actions.
- Remembers the last ping of every server across restarts, updates and process death.
- A **fourth tab you fill yourself** — a subscription, one config, or the live pulse of the route.
- **Material 3 Expressive motion** — stretching loaders, cascading arrivals, rolling readouts and springy releases on every control.
- **A living, layered surface** — a slowly breathing brand aurora behind every page, and one cool-shadow depth system above it, so nothing floats on a dead flat field.
- **A Servers page that shows its hierarchy** — subscription cards with a real usage bar, and their servers nested inside them, smaller in every dimension.

## Servers you can read at a glance

`docs/SERVERS_HIERARCHY_V189.md` is the newest chapter.

**A subscription is a container, and its servers live inside it.** The page used to be a stack of
visually equal rows: a subscription header and its servers shared one outline, one type scale and
one tile size, so nothing but their order said which servers belonged to which plan. The page has
two levels now, and the gap between them is measurable — 16 dp corners and a 1.5 dp outline for
the card, 12 dp corners, no outline and an 8 dp inset for the list inside it; a 16 sp
subscription name over 13 sp server names; a 30 dp protocol tile where a standalone server still
gets 40 dp. Rows are separated by hairlines, never by borders, and the only row that grows an
outline is the one carrying traffic.

**A plan is a bar, not a sentence.** Each subscription shows its used/total figures and, under
them, a real progress bar whose colour is the plan's own state — green to 70 %, amber to 90 %, red
past it — with the percent beside it. Expiry, the provider's website and the auto-update state
stepped down into one quiet secondary line, because they are read once and then ignored.

**Counts live in the controls.** The header's `2 groups • 48 servers` line is gone: the group
capsule badges how many groups the page holds, the protocol capsule badges how many servers the
current scope shows, and the advanced-filter button badges how many of its own switches are on.
The whole rail dropped a step (38 → 32 dp) to pay for it.

**Failure is quiet.** A server a probe could not reach fades and shows a cross in a muted tone;
red stays for a measured latency that is genuinely bad. One table
(`MarbleServersHierarchy.kt`) owns the sizes and the plan arithmetic, and
`ServersHierarchyV189Test` pins both, so a nested row can never quietly grow back to the size of
the card that holds it.

## A face for every server type, and a calmer Settings

`docs/PROTOCOL_IDENTITY_V188.md` is the chapter before it.

**Every wire scheme now has its own minimal identity.** VLESS ripples, VMESS sends an envelope,
Trojan hides behind a shield, Shadowsocks wears the sock, Hysteria2 pushes twin chevrons,
WireGuard carries a key, SSH keeps a prompt, SOCKS a tunnel ring and HTTP a request/reply swap —
each on a circular tile with its own tone, the country flag riding the rim, and a tiny badge
under the name. Both server lists (Servers page and Home) share that one anatomy: type tile,
name, endpoint, and the latency in its own right-aligned stat column with a three-bar quality
meter — no longer a lone number floating in the middle of a row.

**Settings answers each question once.** The four doors that used to ask "which core?" (Engine
& tunnel, Tunnel core, Xray core settings, sing-box extended) are one door now, badged with the
running core's name; the duplicate *General & servers* row and the duplicated *Fourth tab* card
are gone, with nothing lost — the engine page owns the switch, the pinned versions and the
delay test. And the bottom dock no longer fades when you tap a tab: glass is reserved for real
scrolling.

## Motion that feels like the newest Android

`docs/EXPRESSIVE_MOTION_V186.md` is the newest chapter.

**Every movement in MarbleNG now speaks Material 3 Expressive**, the motion language shipping on
current Android — built entirely from primitives every supported device has, so minSdk stays 26
and no behavior changes. One new library (`MarbleExpressive.kt`) holds the emphasized curves,
the duration ladder, the release springs and the pure, unit-tested wave math; the whole product
was rewired through it:

- **Loaders stretch.** Every spinner and progress bar — the busy top bar, the probe strip, the
  securing rings of all five connect silhouettes, refresh and testing spinners down to 11 dp —
  is now a wavy indicator whose arcs grow long and snap short on the shared frame clock, the
  signature rhythm of the newest Android.
- **Screens arrive in cascades.** All four Home themes, the Servers library, the Settings hub,
  the custom dock page and the permission dialog stagger their content in one 45 ms step apart,
  through a self-disarming entrance window: a screen arrives once, and scrolling or filtering
  never replays the arrival.
- **Numbers roll.** The home status word, the ping badge, the live meter headline and the metric
  cards exchange values on the emphasized pair — the new value decelerates up into the fixed
  slot while the old one accelerates away. Nothing around a readout ever moves.
- **Releases spring.** Every press lets go with one visible overshoot; a short drag on either
  slide-to-connect control squishes the knob against the track wall before it settles; buttons
  morph their corner radius while pressed; selections and session flips pop once as
  acknowledgement.
- **Navigation has depth.** Pages parallax and shrink inside the pager, Home → connection
  detail runs a container transform, and Settings sub-pages ride the shared X axis.
- **A protected tunnel reads as alive.** The status pip emits a soft double ring every 2.6
  seconds — strictly inside its own 13 dp slot.

The guarantees survive the elastic surface: the V135 opacity-only ping reveal, the compact
banner geometry, the slide thresholds and park behavior, deterministic metric bands, and full
respect for the system "remove animations" setting — with motion off, every spinner, ring and
cascade rests at its exact target value.

## A page with air and light in it

`docs/AURORA_AND_DEPTH_V191.md` is the newest chapter.

**The interface stopped being a flat field.** Every page now sits on a calm brand *aurora* — the
quiet cloud gradient with three large soft radial glows over it (electric blue off one top
corner, bright ice off the other, a deep navy pool at the floor), breathing so slowly it reads as
atmosphere rather than animation, frozen completely when animations are off. Above it, every
raised surface rejoined the physical world with one rule: **one cool brand-tinted shadow, never a
grey one** — navy-cast in Light, an electric glow lift on AMOLED. Home cards lift 3 dp (the
selected route 7, animated on the spring), Settings hub groups carry their section's tone as a
lit wash down the card, subscription headers on the Servers page glow faintly at their top edge,
and the dock's selected pill finally reads as selected at a glance — with the light-theme glass
bug (the bar vanishing mid-scroll) fixed in the same breath. The PrismPanel glow, tuned in some
earlier life to a maximum of 3.6 % alpha, is now actually visible.

## The Material refresh

`docs/MATERIAL_REFRESH_V185.md` is the previous chapter.

**MarbleNG now wears the newest Google Material language.** The type ramp follows the Material 3
scale with unambiguous steps between display, headline, title and body; cards, containers and
borders move to Material You's calm surface-container finish (cool near-white light surfaces,
navy-lifted steps over AMOLED black); every shape rounds one step up and touch targets meet the
Material baseline; and the whole Canvas icon family strokes at the Material Symbols weight,
larger in every slot — including a taller, rounder floating dock with bigger glyphs. minSdk
stays 26 and no behavior changes: this is the design system only.

## A personal fourth tab, and a calmer Home

`docs/DOCK_SLOT_AND_COMPACT_BANNER_V167.md` is the previous chapter.

**The bottom bar has four slots now, and the fourth one belongs to the user.** Choose the live
pulse, one subscription, or one config; then tune its target, caption, glyph, independent accent, and
live-connection badge from Settings → **Fourth tab**. The preview updates with those choices, and
turning the slot off restores the exact three-tab bar without leaving a dangling page.

**The Home status card now has a clear hierarchy:** a compact connection-state line, one route row,
and an optional two-cell live-telemetry grid for download and upload. The grid unfolds only
when the route is connected and the existing speed-display setting is enabled; the floating speed
card is gone, so it no longer competes with the server identity. All Home cards use a flat fill and
hairline instead of box shadows. In Theme 1, the server list owns the flexible, scrollable center lane
and the horizontal slide-to-connect control stays anchored at the page floor.

## Speed and memory of a measurement

`docs/PING_SPEED_AND_REMEMBERED_PING_V160.md` is the previous chapter.

**Real delay is 30–60 % faster with the same number, the same sample budget and the same
accuracy.** It used to open a fresh SOCKS connection and run a fresh TLS handshake *per sample*,
so a three-sample ping paid the whole cold start of the route three times over. Every sample now
runs on the session the first one opened — one handshake, then one real request and one real
response per sample — which is what the Rank sweep has done since V156 and what v2rayNG calls
"attempt two reuses the same verified session when the origin permits keep-alive". The quiet gap
between samples moved into the batch so the samples are never a burst an adaptive filter can
learn, the cold sample is still discarded, and the Home ping button now publishes the same number
Rank does.

**URL test is faster too, and the pool that carries it is sized by the device.** The startup loop
that waits for the core napped a flat 60 ms between readiness probes — up to a minute of pure
idleness over a hundred-node sweep — and now ramps 4 → 8 → 16 → 25 ms. The measurement pool is no
longer a constant four: `MeasurementCoreBudget` grants six or eight slots only on devices that
report the cores and the heap to carry them, and a small device keeps the four it has always run.

**The measurement is no longer the one thing the product forgets.** Every result is written down
when it lands and read back on the next launch, bounded at 400 rows, pruned after 30 days and
dropped when its node is deleted — so leaving the app and coming back no longer shows a Servers
list with no latency anywhere.

**Fresh installs open on Theme 2 (Floating) with Google Sans**, and Settings names the tunnel core
next to its own title. All three are first-launch or display-only: an install that already chose a
presentation or a typeface keeps its choice, because the store asks whether a value was ever
written instead of writing the default back the first time it is read.

`docs/SINGBOX_STARTUP_GATE_V162.md` is the newest chapter: a connect on the sing-box engine ended
in `core-start-timeout` after 12 seconds with the kill switch held, and the only line in the core's
log was the benign Android package-list warning every `GOOS=android` core prints. The process had
not died — MarbleNG killed it, because "the core is up" was defined as *the local inbound **and**
the Clash controller*, and the controller is an internal service this core binds in its **last**
start-up stage, after every outbound's post-start walk. So the app waited for the entire core
start-up before it would carry a byte of traffic, and it could not tell the two halves apart in a
report: a core that never opened its inbound (a core this device cannot run) and a core whose
tunnel was up and whose controller never answered (a working route with a missing measurement
surface) printed the same sentence — which also handed the user V157's "update MarbleNG" paragraph
for a fault that has nothing to do with that crash. The wait is phased now: the inbound is the
gate, the controller is a bounded 2.5 s phase whose absence is *reported, not fatal*, `sing-box
check` no longer spends the window the child starts in, and `start-result` prints `inbound=` and
`controller=` separately so the next device's report can be read without re-deriving the stage
table.

`docs/PING_FALSE_FAILED_V159.md` is the previous chapter: URL test and Real delay both worked, yet
occasionally published a perfectly healthy server as `FAILED`. Two budget defects, not routing
defects: the URL test shared **one deadline across its fallback targets**, so the coldest request
of a fresh core (the first, which pays DNS + TCP + TLS + fetch on a ~1 s route) consumed the whole
timeout and left the secondary/CDN fallbacks — warm by then, and built for exactly that moment —
with a budget of zero; and Real delay's two Home paths (live tunnel, and the throwaway tunnel the
disconnected ping builds) fetched **one** origin while the Rank sweep already walked every
`DelayTest` candidate, so one moment of SNI-throttling failed every sample of a perfect route.
`ProbeTargetWalk` now owns one shared walk — every candidate target gets the full budget, the
first answer wins, at most three targets, interruptible between them — and a URL-test throwaway
core that never came up is retried once, because a spawn storm is a fact about the device, not a
verdict about the node.

`docs/SINGBOX_PORT_SOVEREIGNTY_V158.md` is the previous chapter: one device spent eight minutes
refused with `bind: address already in use` on `127.0.0.1:10808` while **both engines reported
`alive=false`** — the socket was held by a core process nothing tracked (an orphan from an earlier
app process Android had killed), nothing on the connect path ever asked the OS who held the port,
and the refusal wore the `core-start:` prefix, so every reader candidate was spawned to die on the
same port before the user was told the profile was "misconfigured". MarbleNG now attributes the
holder through `/proc/net/tcp{,6}` + `/proc/<pid>/fd` and reaps same-UID core binaries
(TERM → KILL) before either engine spawns, names and spares foreign holders, classifies a bind
conflict as the `core-port:` local fault it is (one spawn, no reader walk, `Local port in use` in
the blocked state), reports the exit verdict of every core `stop()`, fixes the wire defect that
let MarbleNG's own domain-target SOCKS5 requests arrive without RFC 1928's length prefix (each
request is now one assembled, single-`write` segment), and counts the
`read fqdn: unexpected EOF` ERROR lines of local clients aborting their own SOCKS handshake as
benign evidence instead of Bug Finder failures.

`docs/SINGBOX_ANDROID_CLI_CRASH_V157.md` is the previous chapter: three shipped builds carried an
Android sing-box artifact that panicked in its own `direct` outbound for **every** profile, because
an app-UID child gets no netlink interface monitor on Android. The product answered in four
remote-sounding vocabularies — `reachable = 0 of 17` for both the URL test and Real delay, a BLOCKED
`Core/configuration error`, and a Bug Finder scan that reported `failures=0` because every core check
it runs is gated on a connection a crashing core never establishes. MarbleNG now compiles the core
from the pinned commit with the upstream nil-guards backported
(`scripts/inject-singbox-android-fix.py`, proven by Go tests in CI before a single ABI is built), asks
the binary whether it can start before it asks any server (`SingBoxCoreSelfTest`), stops a sweep at the
first fault that cannot change mid-sweep (`ProbeLocalFaultGate`), and reports a crashed core as a
crashed core in every app state.

`docs/ENGINE_SELF_HEAL_AND_PING_AIR_V152.md` is the previous sing-box chapter: the shipped 8.0.6 build
could not connect at all on the sing-box engine because the emitted config still carried the
`dns` outbound sing-box removed in 1.13.0 — every profile was refused, the session went BLOCKED,
and failover replayed the same refusal seventeen times. The outbound is gone, the config is
checked by a **self-healing doctor** (`SingBoxConfigDoctor`) that repairs the known core
migrations in place, and an engine-level fault now stops the walk at once instead of replaying it
per candidate: failover to another *node* is pointless when the engine itself refused, and choosing
the other engine stays the user's explicit act in Settings → Tunnel core.

`docs/CONNECTION_ROOT_CAUSE_V132.md` documents the full trace of the connect path and the five
defects found in it, including the two that made a healthy server fail in MarbleNG while it
connected and pinged in another client:

- a background routing-data refresh could veto every connection for minutes at a time;
- the node hostname was resolved by two `https+local` bootstrap literals and nothing else, so two
  filtered resolver IPs made an otherwise working server undialable.

`docs/CORE_MALFUNCTION_ROOT_CAUSE_V133.md` covers the next layer down: a session in which the core
started cleanly and never crashed, while encrypted DNS and route health failed continuously. Every
deadline and threshold in the stack was a constant sized for a fast link, so on a ~1.1 s route each
DoH query expired before the resolver could answer, and the failures then propagated into the
acceleration engine — a 30-minute backoff that could neither decay nor be released, jitter control
that oscillated, a per-socket MSS treated as a path MTU, and an IPv6 verdict that never reached the
emitted config.

`docs/RESOLVER_EVIDENCE_AND_MEASUREMENT_PLANE_V134.md` closes the resolver feedback loop: failures
the core logs are attributed to the endpoint it named, demote it in the emitted resolver order for
the current physical network, and arm parallel query only on that evidence.

`docs/ROOT_CAUSE_V135.md` covers the IPv6/memory/socket triangle: a Wi-Fi network with no global
IPv6 still received an unreachable IPv6 resolver graph and a captured `::/0` route, paid 8 s per
dead resolver on the fragment chain, and re-dialled six times in two minutes with zero pacing
until the OS revoked the VPN permission. The fixes purge resolver families the underlay cannot
dial, gate the IPv6 route capture on real underlay capability, size fragment-chain DNS budgets
from the measured link, and pace every automatic recovery behind an exponential ladder with a
rolling circuit breaker.

## Main navigation

MarbleNG uses three primary tabs: **Home**, **Servers**, and **Settings**.

### Home

- Connect / Disconnect / Cancel / Reset control.
- Exact last-route one-tap reconnect.
- **Only the selected route** — the one server or subscription group you chose, never a list.
- Full TUN / local proxy state.
- Live Ping, Jitter, and Quality.
- Optional node / Xray / mode summary metrics.
- Iran Mode state when enabled.
- Quick access to Rank, Servers, Privacy, and Routing.
- Physical-network label and live upload/download activity.
- Tunnel and kill-switch state.
- Three Home presentations — Signature (the default professional studio), Cosmic orbit and Cosmic immersion — switchable from Settings.
- Five connection controls, one product decision: the round shutter, slide-to-connect, classic power bar, a **stream bar** with a light band travelling right to left, and a **floating pill** docked above the bottom of the page. The last two are pinned to the floor of the page so the primary action is always in reach.
- A **live ping instrument** in a permanent, reserved slot beside the connection control: arc gauge, current value, sparkline of the last probes. It only ever measures the one server you are attached to, it is revealed — with an opacity fade, never a layout change — only once the connect control itself shows CONNECTED, and its chrome complements the connect button (same elevated glass, the control's own state tone in its hairline), so nothing on the page ever shifts when it appears.
- A **shortcut deck** above the status banner: add server, paste, QR code and a permanent ping readout.
- Signature studio extras, every layer optional from Settings: a draggable floating connect button, a status banner (Home-only or on all pages), a configurable corner shortcut.
- Real connection ping: parallel HTTPS first-byte + tunnel RTT probes with a healthy-minimum ladder, so a busy edge no longer reads as "no response".
- Seamless loop animations in every style — no visible start or end boundary.

The floating bottom tab bar is completely still: no breathing, no resize, no selection pulse. Only its colour, shadow depth and selection wash animate, and all of them use overshoot-free tweens so a tap can never flash the bar.

The Home title and main connection surface use a fixed layout so changing runtime status text does not move the Connect control. The status title and sentence above the Connect ring are anchored to a reserved block, so a shorter or longer sentence swaps in place instead of pulling the control up and down. Ping and uptime readouts auto-shrink and ellipsize so they never overflow their box in any style.

### Servers

The **Servers** tab manages subscriptions, source buckets, and nodes. It is a card system: one page
header, one search field with a filter rail under it, one collapsible header per group and one
independent card per server. Nothing is flush against anything else — every surface keeps its own
radius and hairline, and every colour comes from the active theme palette.

#### Header, search and filters

- Bold **Servers** headline with a live `2 groups • 18 servers` count.
- Two round controls on the right: `+` opens Add node, and the sort control opens a fully rounded
  dropdown (Default, Name, Name (Z-A), Ping, Country, Protocol) that ticks the active choice.
- One pill-shaped search field: name, protocol, host, transport, security or country.
- A filter rail under it: the active group capsule, an **All protocols** capsule that lists every
  protocol with its live count, an advanced-filter menu (Group by country, Only reachable,
  Max ping Off/100/200/500 ms, Reset) and a control that measures every server of the scope at once.

#### Groups

- Add **HTTPS** remote subscription URLs.
- Create local source buckets.
- Paste configs from the clipboard.
- Import config files.
- Refresh one selected source or all remote sources.
- Rename, edit, or delete a source.
- Filter by All, Manual, or one subscription.
- Source-managed and user-owned local profiles are kept distinct.

A group header is one rounded box. Folded it shows a chevron, the name, the server count and the
auto-update state; open it grows its own facts — the plan usage box (`384.7 GB / ∞`), the expiry
line, a **Website** capsule, a refresh control and a three-dot menu (manage, refresh, copy URL, copy
all servers, ping, rank, show only, delete). Redundant source-dashboard chrome and duplicate counters
stay removed.

#### Server cards

Each server is its own 16dp card: the country on the left, the identity column in the middle (bold
name, a protocol badge such as `VLESS/REALITY` or `VMESS/TLS/H2`, then the endpoint in faint ink)
and a latency capsule plus a three-dot menu on the right.

- Connect directly to a node; the connected card keeps its geometry and only changes frame and one word.
- Per-server menu: **Edit**, **Copy link**, **Export QR code**, **Ping**, **Move to group**,
  **Details**, **Copy Xray JSON**, **Edit Xray JSON**, **Duplicate to Manual** and **Delete**.
- The QR export is rendered on device by Marble's own encoder — no network, no image dependency.
- Sort by Default, Name, Name (Z-A), Ping, Country or Protocol; never-measured servers sort last
  rather than pretending `0 ms` is fastest.
- Run real full tests, or measure a whole group at once.
- Rename, move between groups, or delete nodes.
- Copy original config/share text or generated Xray JSON; edit Xray JSON for supported profiles.
- Duplicate a profile into the always-on Manual storage.
- Swipe right for the rename dialog.
- See per-card queued/testing progress.

The latency capsule never resizes: an unmeasured server reads `0 ms` in the theme's danger tone and
tells TalkBack the truth — that nothing has been measured yet.

#### Add node

`+` opens a full-screen sheet with rounded top corners: **Node**, **Chain** or **Subscription**.

- Name, a comprehensive Protocol dropdown (VLESS, VMess, Trojan, Shadowsocks, Hysteria2, WireGuard,
  SSH, SOCKS5, HTTP, HTTPS, Xray JSON — plus the protocols this core cannot dial, listed disabled
  with the reason), a wide Server field beside Port, then UUID / password, Flow, Transport and
  Security dropdowns.
- **Save** stays disabled until the config is complete, and the sentence under the form says exactly
  which field is missing — the same check the builder itself uses.
- Paste and file import land in the group the page is currently showing.

### Settings

Common settings are visible first; advanced network controls are behind **Expert controls**.

Main areas include:

- Appearance (connection style, Signature studio customization, accent color, night outline style — subtle / bold / colored / hidden, light/dark theme, Vazir or System font, language)
- Connection
- Testing & ping
- Split tunneling
- Notifications
- Subscriptions
- Regional protection / Iran Mode
- Marble Intelligence
- DNS
- Routing
- Fragmentation & Mux
- Chain proxy
- Bug Finder

## Last-route persistence

A successful connection is treated as durable user intent.

1. When a profile reaches `CONNECTED`, MarbleNG saves that profile ID.
2. Closing or killing the app does not erase it.
3. On the next launch, Home resolves that profile from the current Servers list.
4. Pressing **Connect** reconnects that exact profile first.
5. If the profile was deleted, MarbleNG clears the stale reference and falls back to automatic selection.

## Supported inputs

### URI / subscription inputs

- VLESS
- VMess
- Trojan
- Shadowsocks
- Hysteria2 / HY2
- SOCKS / SOCKS5
- HTTP / HTTPS
- SSH
- Base64 subscription payloads
- Plain link lists
- Xray JSON

### Manual editor

- VLESS
- VMess
- Trojan
- Shadowsocks
- Hysteria2
- HTTP
- HTTPS
- SOCKS5
- SSH
- WireGuard-style manual configuration
- Raw/custom Xray JSON

### Xray transports and security

Depending on the profile, MarbleNG preserves modern Xray stream settings such as:

- TCP / raw
- WebSocket
- gRPC
- HTTP/2
- HTTPUpgrade
- XHTTP / SplitHTTP
- mKCP
- TLS / REALITY-related stream settings
- SNI
- ALPN
- fingerprints
- advanced custom Xray JSON

## Connection architecture

Full TUN mode:

```text
Android apps
    ↓
Android VpnService / TUN
    ↓
hev-socks5-tunnel
    ↓
local Xray SOCKS path
    ↓
Xray outbound
    ↓
selected remote node
    ↓
Internet
```

Local proxy mode exposes the configured loopback proxy without forcing the whole Android device through TUN.

## Testing and ranking

### Quick TCP ping

- Fast host/port reachability.
- Endpoint de-duplication for large aggregator subscriptions.
- Per-node progress updates.

### Real Xray verification

- Uses a real Xray path.
- Tests proxy usability rather than merely checking whether a TCP port accepts connections.
- Keeps tunnel evidence separate from lightweight TCP evidence.

### Smart rank

- Can rank the whole Servers list or the currently selected group.
- Uses healthy tunnel evidence, score, and latency.
- Tests the enabled scope without an artificial eight-node cap.

## Live telemetry

While connected, MarbleNG can expose:

- route RTT
- jitter
- route quality score
- download rate
- upload rate
- physical-network information
- active route state

Unknown metrics remain unknown rather than being presented as fabricated zero-quality evidence.

## Marble Intelligence

Adaptive features include:

- network-scoped route history
- persistent health evidence
- connection race
- smart fallback
- network-change recovery
- adaptive MTU
- adaptive DNS ordering
- IPv4 / IPv6 adaptation
- adaptive throughput testing
- UDP / QUIC health evidence
- thermal-aware testing
- adaptive tunnel buffers
- workload profiles
- continuous route optimization
- Marble Turbo connection tuning

### Marble Turbo

When enabled, MarbleNG can compare measured transport strategies on the selected route and remember the better method without arbitrarily changing the user's intended exit profile.

## Privacy and fail-closed behavior

- Full-device TUN fail-closed behavior.
- Kill-switch state.
- DNS interception.
- Encrypted DNS / DoH configuration.
- Identity Guard.
- Split-tunnel visibility.
- Privacy audit through the active proxy.
- Exit IP and DNS observation.

For stronger OS-level protection, Android's **Always-on VPN** and **Block connections without VPN** can also be enabled.

## DNS

Expert DNS controls include:

- TCP/UDP port 53 interception
- primary and secondary TUN DNS
- primary and secondary DoH
- adaptive DoH ordering
- adaptive dual-stack behavior
- IPv6 enable/disable
- IPv6 preference
- `UseIP`, `UseIPv4`, `UseIPv6`, and `UseSystem`
- common resolver presets

## Routing

Routing modes:

- Proxy all
- Private direct
- Geo direct
- Custom

Controls include:

- GeoIP / GeoSite tags
- private-network bypass
- direct domains
- proxy-only domains
- blocked domains
- direct IPs
- blocked IPs/CIDRs
- domain strategy
- ad blocking
- `geoip.dat`
- `geosite.dat`
- Xray routing-policy verification

## Iran Mode / regional protection

Policies:

- Auto
- Always on
- Off

Capabilities include:

- underlay / ISP classification
- confidence-based detection
- filtering-technique observations
- domestic direct-routing policy
- routing presets
- adaptive countermeasures
- optional deep probing

## Split tunneling

- All apps through VPN
- Only selected apps
- Bypass selected apps
- Installed-app picker for package-level routing

## Notifications

MarbleNG uses Android's foreground-service notification while its connection service is active.

Optional alerts include:

- connection events
- recovery events
- privacy warnings
- network changes
- subscription events
- core updates
- live telemetry
- configurable cooldown
- Android notification channel management

In-app Snackbar notices use a flat surface with **no heavy black drop shadow**.

## Subscription management

- startup refresh for stale remote sources
- configurable refresh cadence
- manual selected-source refresh
- refresh-all
- source metadata when supplied by providers
- safe source deletion
- user-owned local profile preservation

## SSH

The manual SSH path carries TCP through the protected local adapter. Unsupported UDP behavior remains fail-closed.

## WireGuard-style manual input

The manual editor exposes fields for:

- private key
- local address/CIDR
- peer public key
- pre-shared key
- allowed IPs
- reserved values
- keepalive
- MTU
- userspace mode

## Fragmentation and Mux

Expert controls include:

- TLS ClientHello fragmentation
- fragment packets
- fragment length
- fragment interval
- Mux
- TCP concurrency
- XUDP concurrency
- UDP/443 policy
- adaptive Fragment
- adaptive Mux

## Chain proxy

An optional two-hop route can use a selected second profile as the next hop.

## Bug Finder and diagnostics

Built-in diagnostics cover areas such as:

- Xray runtime
- TUN / HEV state
- app connection state
- active profile
- routing assets
- connection history
- runtime logs

Debug Mode can export technical reports for development and bug investigation.

## In-app updates

By default, MarbleNG checks the latest stable GitHub Release when the app returns to the foreground.

When a newer semantic version exists:

- a native update dialog appears;
- version and release notes are displayed;
- the GitHub Release can be opened;
- automatic update checks can be disabled in Settings.

## Versioning

MarbleNG uses semantic release names with a monotonic Android `versionCode`.

Typical patch progression:

```text
1.0.2 → 1.0.3 → ... → 1.0.9 → 1.1.0
```

Larger changes can request minor or major jumps. Published release tags are intended to be immutable.

## Android ABIs

Signed releases target:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- `x86`

The release pipeline can also publish a universal APK.

## Native cores

Pinned in `core-lock.json`:

- Xray-core
- hev-socks5-tunnel

Pinning makes native-core changes explicit and reproducible.

## GitHub Actions build

The signed build workflow:

1. checks out full history and tags;
2. provisions JDK and Android SDK/NDK;
3. reads pinned native-core versions;
4. builds native dependencies;
5. restores the persistent Android signing identity from GitHub Actions secrets;
6. validates the keystore and private key;
7. calculates the semantic app version;
8. builds signed APKs;
9. verifies APK signatures;
10. uploads Actions artifacts;
11. publishes a GitHub Release.

No signing private key is committed to the repository.

### Play Protect-safe installation

Release packaging now refuses to produce an installable `assembleRelease`/`bundleRelease` artifact
without `signing.properties`. This prevents an unsigned local APK from being mistaken for the
trusted MarbleNG release. Install the APK from the signed GitHub Actions artifact, or upload that signed APK to Google Play
internal testing while preserving the existing release certificate;
do not rotate the certificate between builds. The workflow verifies the resulting APK certificate
with `apksigner` before publishing it. Play Protect reputation still belongs to Google Play and
cannot be bypassed by app code.

## Local build

The GitHub Actions workflow is the reference build environment.

Typical local flow:

```bash
./scripts/prepare-native.sh
./gradlew assembleRelease
```

Check the current workflow and Gradle files for the exact SDK/NDK/toolchain versions.

## Repository docs

Additional implementation notes live under `docs/`, including regional protection, Marble Intelligence, UI, routing, and runtime documentation.

## Security notes

- Never commit Android signing keys.
- Never commit subscription credentials.
- Treat imported proxy URLs as secrets.
- Debug output should avoid authentication material.
- Keep Android's system VPN protection enabled when leak prevention is critical.


## System integrity preflight

`scripts/system-integrity-check.py` is a fast architecture-level regression guard that runs in
source verification and again before the signed release build. It checks that Android lifecycle,
persistence, exact Library identity, Xray/HEV ownership, encrypted DNS, temporary test processes,
Marble Intelligence, diagnostics and release invariants still agree with one another.

This complements — rather than replaces — Kotlin compilation, Xray config validation, JNI symbol
verification, APK signature verification and the in-app Bug Finder runtime evidence.

## Project status

MarbleNG is under active development. The `main` branch and latest signed GitHub Release are the authoritative sources for the current feature set.
