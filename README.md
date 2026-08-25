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
- Smart ranking for the whole Library or one selected source.
- Solid White, Dark, and System themes.
- Font-independent Canvas vector icons for critical actions.
- Smart GitHub Release update checks.
- Signed multi-ABI APKs built by GitHub Actions.

## Main navigation

MarbleNG uses three primary tabs: **Home**, **Library**, and **Settings**.

### Home

- Connect / Disconnect / Cancel / Reset control.
- Exact last-route one-tap reconnect.
- Current selected route.
- Full TUN / local proxy state.
- Live Ping, Jitter, and Quality.
- Optional node / Xray / mode summary metrics.
- Iran Mode state when enabled.
- Quick access to Rank, Library, Privacy, and Routing.
- Physical-network label and live upload/download activity.
- Tunnel and kill-switch state.

The Home title and main connection surface use a fixed layout so changing runtime status text does not move the Connect control.

### Library

Library manages subscriptions, source buckets, and nodes.

#### Sources

- Add **HTTPS** remote subscription URLs.
- Create local source buckets.
- Paste configs from the clipboard.
- Import config files.
- Refresh one selected source or all remote sources.
- Rename, edit, or delete a source.
- Filter by All, Manual, or one subscription.
- Source-managed and user-owned local profiles are kept distinct.

The Library UI keeps only functional controls: source selection, sort, Refresh, Ping, and Rank. Redundant source-dashboard chrome and duplicate counters are intentionally removed.

#### Nodes

- Search by name, protocol, host, transport, or security.
- Sort by Ping, Name, Protocol, Source, or reverse order.
- Connect directly to a node.
- Run real full tests.
- Rename or delete nodes.
- Open connection details.
- Copy original config/share text.
- Copy generated Xray JSON.
- Edit Xray JSON for supported profiles.
- Duplicate a profile into Manual storage when enabled.
- Swipe for quick test / rename actions.
- See per-card queued/testing progress.

### Settings

Common settings are visible first; advanced network controls are behind **Expert controls**.

Main areas include:

- Appearance
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
3. On the next launch, Home resolves that profile from the current Library.
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

- Can rank the whole Library or the currently selected source.
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
