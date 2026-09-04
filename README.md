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

## Main navigation

MarbleNG uses three primary tabs: **Home**, **Servers**, and **Settings**.

### Home

- Connect / Disconnect / Cancel / Reset control.
- Exact last-route one-tap reconnect.
- Current selected route.
- Full TUN / local proxy state.
- Live Ping, Jitter, and Quality.
- Optional node / Xray / mode summary metrics.
- Iran Mode state when enabled.
- Quick access to Rank, Servers, Privacy, and Routing.
- Physical-network label and live upload/download activity.
- Tunnel and kill-switch state.
- Five connection presentations — Signature (the default professional studio), Organic, Orbit, Nebula, and Blueprint — switchable from Settings or from the style chips at the bottom of Home.
- Signature studio extras, every layer optional from Settings: a draggable floating connect button, a status banner (Home-only or on all pages), a top-right action cluster (add server, ping grab, user-selected shortcut, overflow menu), a Home rail of your selected servers with glass / colored / plain card backgrounds.
- Real connection ping: parallel HTTPS first-byte + tunnel RTT probes with a healthy-minimum ladder, so a busy edge no longer reads as "no response".
- Seamless loop animations in every style — no visible start or end boundary.

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
  **Details**, **Copy Xray JSON**, **Edit Xray JSON**, **Duplicate server** and **Delete**.
- The QR export is rendered on device by Marble's own encoder — no network, no image dependency.
- QR **import** has two doors: scan a code with the camera, or pick a screenshot/photo from the
  gallery. Both decode on device (ZXing core) and the gallery door needs no permission at all.
- Sort by Default, Name, Name (Z-A), Ping, Country or Protocol; never-measured servers sort last
  rather than pretending `0 ms` is fastest.
- Run real full tests, or measure a whole group at once.
- Rename, move between groups, or delete nodes.
- Copy original config/share text or generated Xray JSON; edit Xray JSON for supported profiles.
- Duplicate a profile into the local source, so a copy is always user-owned and survives a
  subscription refresh.
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
- Paste and file import land in the group the page is currently showing, and the sheet names that
  group so it is never a guess where a server was filed.
- **Subscription** needs only the link: paste it and the group is named after the provider's host.
  The sheet closes only when the source was really created, so a refusal (plain HTTP, a duplicate
  link) keeps the form on screen and says why. A subscription link pasted into the generic import
  box is recognised and creates the source instead of being handed to the config-link parser.

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

One setting — Settings → Tests → Ping — drives every measurement in the product: the Home ping
button, a group's ping and the page-wide ping.

### Smart ping (default)

- A cheap reachability gate first: one TCP handshake with Happy-Eyeballs address racing, and one
  resolver round trip as a second opinion when the handshake comes back empty. No child process,
  so a dead endpoint is reported in well under a second.
- Only a gate-passer then pays for the verified HTTPS measurement through a real Xray tunnel, with
  a start-up budget that survives a busy phone.
- A gate-passer whose verified phase could not complete is reported **reachable but unverified**
  with its real handshake latency, never as a failure — a filtered network and a loaded CPU used to
  paint every healthy server red. Unverified results are kept out of tunnel intelligence, so they
  can never teach the ranker that an unproven node is a working route.

### Address-level ping

- TCP, ICMP, HTTP and DNS measure the endpoint or the underlay path directly.
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
