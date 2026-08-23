# 🪀 MarbleNG

**A private, fast and smart Xray VPN client for Android.**
It takes your proxy links, tests them for real, picks the best one, and sends your whole phone through it — safely.

Native Android port of the Xray Genius Termux client, powered by **Xray-core** + **hev-socks5-tunnel** and an Android `VpnService`.

---

## ⚡ In one minute

1. 📥 Add a subscription link (or import a file / paste configs).
2. ▶️ Tap **Connect securely**.
3. 🛡️ Your phone is protected. MarbleNG keeps checking the route and repairs it if it breaks.

---

## 📱 The five screens

### 🏠 Home
- 🔘 One big **Connect / Disconnect** button.
- 🟢 Live status: `Protected` • `Connecting` • `Blocked` • `Ready`.
- 📊 **Performance score** (0–100) from real latency, reliability and route evidence.
- ⏱️ Live numbers: ping (RTT), download and upload speed.
- 🚀 Shortcuts: measure routes, open Library, run a privacy audit, jump to Routing.

### 📚 Library
- ➕ Add subscriptions by URL, or 📂 import configs from a file.
- 🔄 Refresh one source or **refresh all**.
- ✏️ Manage a source: rename, change URL, view only its nodes, delete it (with its nodes).
- 📈 Data quota and expiry shown when the provider sends them.
- 🔍 Search by name, protocol, host, transport or security.
- 🗂️ Filter by source: **All**, **Manual**, or any subscription.
- ↕️ Sort by **Ping**, **Score**, **Name**, **Protocol**, **Source** — and reverse it.
- 👉 Swipe a node: right = **Test**, left = **Edit name**.
- ⋮ Menu: real tunnel test, rename, delete.
- ▶️ Tap the node action to connect straight to it.
- 🧪 **Test all** measures every node with the method you picked in Settings → Testing & ping.
- 🔴 **Live progress on the cards themselves**: each node shows `Queued` → `Testing…` with its own
  bar, and its score appears the moment that node finishes — no more waiting for one anonymous bar
  at the top of the screen. Refreshing a source animates on that source's card.

### 📊 Quality
- 🎛️ Test modes: **Reliable**, **Balanced**, **Fast**, **Turbo**.
- ▶️ **Run performance test** ranks your library with real tunnels, showing `Measuring 12 / 40`
  and the node being probed right now.
- 💍 Score ring for the active (live) route or the best measured one.
- 📉 RTT, reliability, live sample count and the test method in use.
- 🏆 **Measured routes** table, ranked, with a one-tap **Use** button.

### 🌐 Network
- 📶 Your current link: Wi-Fi / cellular / ethernet, metered or not, IPv4 / IPv6, MTU, up/down estimate, validated or not.
- 🔁 **Refresh network check** re-scans the link.
- 🧠 **Marble Intelligence** status: effective MTU, thermal budget, stored history, last decision.
- 🇮🇷 **Regional protection**: detected ISP and confidence when Iran Mode is on.
- 📚 Recent measurements (read-only evidence).

### ⚙️ Settings
Simple controls first — everything technical hides behind **Expert controls**.

---

## 🧰 All options

### 🎨 Appearance
- ✨ **Glass White** by default, plus System and Dark choices.
- 🫧 One shared spring-based Kinetic Glass motion engine with responsive press feedback.
- 🧑‍🔬 **Expert controls** switch (now remembered between visits).

### 🔌 Connection
- 📱 **Full TUN** — the whole device goes through the tunnel.
- 🧦 **Local SOCKS5** — only apps you point at `127.0.0.1:<port>`.
- 🔢 Local SOCKS port.
- 💾 Remember last node and reconnect to it.

### 🧪 Testing & ping
- 🧠 **Smart** (default) — quick TCP gate to drop dead servers, then a real tunnel test on the rest.
- 🛡️ **Real tunnel** — every node gets a real Xray process and a real HTTPS request. Slowest, and
  the only method that proves a node truly works.
- ⚡ **TCP ping** — TCP handshake time to the server (tcping). Very fast and light.
- 📡 **ICMP ping** — classic system ping. Fast, but many servers and carriers drop ICMP.
- 🔢 Pings per node, timeout per try, nodes per test run.
- 📶 Optional download-speed measurement (off by default — it is the slowest part of a test).

### 🧩 Split tunneling
- 🌍 **All apps** through the tunnel.
- ✅ **Only selected** apps.
- 🚫 **Bypass selected** apps.
- 🔎 Searchable list of installed apps with checkboxes.

### 🔔 Notifications
- 🔐 Grant notification permission, 🧪 send a test alert, 🎚️ open Android channels, 🧹 clear alerts.
- 📟 Live status in the notification (ping, quality, ↓/↑ rates).
- 🔕 Per-event switches: connection, recovery/failover, privacy warnings, network changes, subscription updates, core updates.
- ⏲️ Alert cooldown (5–300 s).

### 🔄 Subscriptions
- 🕒 Auto-refresh stale sources at startup.
- ⏳ Refresh cadence (1–168 h).
- 🔄 Refresh all now.

### 🇮🇷 Regional protection (Iran Mode) — *expert*
- 🤖 **Auto** (detect ISP) • 🔒 **Always on** • ⛔ **Off**.
- 🛠️ Apply countermeasures (fragmentation profile, resolver order, MTU ceiling, failover posture).
- 🏠 Send domestic Iranian traffic direct (fast, less tunnel usage).
- 🔬 Fingerprint the filtering: DNS injection, SNI resets, port allowlists, UDP blocking.
- 📣 Notify when Iran Mode engages, 🔁 re-scan now.

### 🧠 Marble Intelligence — *expert*
- 🧠 Adaptive engine on/off, using network-scoped history.
- 🧷 Maximum config compatibility (keeps outbound dependencies, verifies with `xray run -test`).
- 🧪 Verified performance auto-tune (A/B Fragment & Mux, keep only real gains).
- 🚀 **Marble Turbo**: when you connect, the engine *executes* real methods on the node you picked — TLS fragmentation shapes, Mux reuse, IPv4-first endpoint resolution — measures ping and speed for each, and keeps the winner. Tuning budget, methods per pass, live re-tuning interval, ping trigger, minimum gain to re-dial, adaptive tunnel datapath, and a **Boost active route now** button. The exit IP never changes, so it works with Identity Guard on.
- 🛰️ **Continuous Autopilot**: interval, challengers per cycle, deep-speed cycle, switch cooldown, evidence confirmations, protect heavy downloads.
- 💾 Persistent route intelligence (EWMA health per network fingerprint).
- 🏁 **Connection race** + race width — first healthy route wins.
- 🪂 **Smart fallback** + depth, and auto-connect after kill switch.
- 🔁 Network-change recovery (Wi-Fi ⇄ cellular).
- 📏 Adaptive MTU with floor and ceiling.
- 🌡️ Thermal-aware benchmarking, 📶 adaptive throughput test, 📡 UDP/QUIC probe.
- 🎯 Workload profile: `AUTO`, `INTERACTIVE`, `STREAMING`, `STABILITY`, `STEALTH`.
- 🛡️ **Privacy Sentinel** badges: coverage, DNS capture, kill switch, bypassing apps.

### 🌐 DNS — *expert*
- 🕳️ Intercept classic DNS (port 53) into Xray's encrypted DNS.
- 🥇 Adaptive DoH ordering (measures the fastest resolver through the proxy).
- 🔀 Adaptive IPv4 / IPv6 selection.
- ⚡ Presets: Cloudflare, Google, Quad9.
- ✍️ Custom TUN DNS 1/2 and primary/secondary DoH URLs.
- 🧭 Query strategy: `UseIP`, `UseIPv4`, `UseIPv6`, `UseSystem`.

### 🧭 Routing — *expert*
- 🇮🇷 **Restore recommended Iran policy** in one tap.
- 🏠 Bypass Iranian traffic (`geosite:ir` + `geoip:ir` go direct).
- 🚫 Aggressive ad blocking (`geosite:category-ads-all`).
- 🧱 Modes: **Proxy all**, **Private direct**, **Geo direct**, **Custom**.
- 🗃️ Geo data: bundled fallback, custom `geoip.dat` / `geosite.dat` URLs, **Prepare**, **Update now**, **Verify with Xray**.
- 🏷️ Direct GeoIP / GeoSite tags, bypass private networks, domain strategy.
- 📝 Exceptions: always-proxy domains, block domains, block IP/CIDR, custom direct domains and IPs.

### ✂️ Fragmentation & Mux — *expert*
- 🧬 Adaptive Fragment (only after real TLS/REALITY interference) and adaptive Mux.
- ✂️ TLS ClientHello fragmentation: packets, length, interval.
- 🧵 Mux / XUDP: TCP concurrency, XUDP concurrency, UDP-443 policy (reject / allow / skip).

### 🔗 Chain proxy — *expert*
- 🪢 Two-hop route: entry node → chosen exit node, keeping the exit's transport intact.

### 🧰 Maintenance
- 🩺 **Check the app** — runtime, native bridge and routing-asset checks.
- 📜 **Diagnostic log** — shareable technical detail for bug reports.
- 🕘 **Connection history** — recent connections and why they changed.
- ♻️ **Reset all settings** back to the safe defaults.

---

## 🔐 Privacy & safety

- 🧱 **Fail-closed kill switch** — if the core dies, the VPN interface stays up and traffic is blocked instead of leaking.
- 🪪 **Identity Guard** — keeps one stable public exit IP for your session; no silent hopping to another IP.
- 🚫 **No DNS leaks** — classic DNS is captured and sent through encrypted DoH inside the tunnel.
- 🔍 **Privacy audit** — checks your exit IP, location and DNS servers *through* the tunnel.
- 🙈 No SSID, IMSI, config secrets or passwords are ever logged.
- 📵 The VPN app is excluded from its own tunnel, so there is no routing loop.
- 🧯 For the strongest system-level protection, also enable Android's **Always-on VPN → Block connections without VPN**.

---

## 🔧 Supported inputs

| What | Details |
| --- | --- |
| 🔗 Protocols | VLESS, VMess, Trojan, Shadowsocks, Hysteria2, SOCKS, HTTP/HTTPS |
| 📄 Configs | Full Xray JSON, JSON arrays, base64 subscriptions, plain link lists |
| 🚇 Transports | raw/TCP, WebSocket, XHTTP/SplitHTTP, HTTPUpgrade, gRPC, HTTP/2, mKCP |
| 🔒 Security | TLS, REALITY, uTLS fingerprints, ALPN, ECH |
| 📲 ABIs | arm64-v8a, armeabi-v7a, x86_64, x86 |

---

## 🏗️ How it works

```
Apps ➜ Android TUN ➜ hev-socks5-tunnel ➜ Xray SOCKS5 ➜ your server ➜ Internet
```

- 🧊 Xray-core is built from the exact tag pinned in `core-lock.json`.
- 🧱 `hev-socks5-tunnel` is built from its exact tag with the official Android NDK makefiles.
- 🆕 `scripts/update-core-lock.sh` finds newer upstream releases.
- 🤖 GitHub Actions builds signed universal and per-ABI APKs.

## 💻 Local build

Install Android SDK 37, NDK 28.2.13676358, JDK 17, Go and Git, then:

```bash
./scripts/prepare-native.sh
./gradlew assembleRelease
```

GitHub Actions is the recommended path — it pins and provisions the whole toolchain automatically.

## ✍️ Signing

No signing key lives in Git. The Termux injector creates the signing material once, stores it as GitHub Actions secrets, and never overwrites existing secrets, so the app identity stays stable across releases.

## 📖 More docs

- 🇮🇷 [Iran Mode](docs/IRAN_MODE.md) — detection model, recognised ISPs, every countermeasure.
- 🧠 [Marble Intelligence](docs/MARBLE_INTELLIGENCE.md) — the adaptive engine.
- 🫧 [Kinetic Glass UI](docs/AETHER_FLOW.md) — white-glass tokens, motion engine and performance rules.
