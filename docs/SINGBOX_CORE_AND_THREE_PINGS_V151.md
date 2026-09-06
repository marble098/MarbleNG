# V151 — sing-box extended as a second engine, three ping methods, and the throughput bugs behind "connected but slow"

Five asks, one branch:

1. Remove the "Show complete IP information" caption from Home.
2. Make Xray core, hev-socks5-tunnel and sing-box extended agree everywhere the product reports a version.
3. Fix the low speed and bad ping on the Xray path.
4. Fix the customizer layout, offer Home style 2's floating button in it, and let the Customize affordance be hidden.
5. Delete the ping-method list, replace it with Real delay and TCP ping from PattNG plus a URL test from sing-box extended.

---

## 1. The Home IP strip

`HomeThemeIosSlider`'s identity strip used to print the address, the country code, and then nine
words saying what tapping the strip does. The whole strip is one tap target, the row is 32 dp tall,
and the caption only ever had room to ellipsize itself.

The words are gone from the screen and survive as the INFO glyph's `contentDescription`, so the
strip is still readable out loud and still says what it does. `Tr.now.ipDetails` is unchanged in
both languages; only the place it is drawn moved.

## 2. One pinned version, read everywhere

`core-lock.json` is the single source:

```json
"xray":    { "repo": "XTLS/Xray-core",             "tag": "v26.7.28",               "channel": "prerelease" }
"hev":     { "repo": "heiher/hev-socks5-tunnel",   "tag": "2.17.1",                 "channel": "latest-release" }
"singbox": { "repo": "shtorm-7/sing-box-extended", "tag": "v1.14.0-extended-2.7.1", "channel": "beta" }
```

Every consumer reads it rather than repeating it:

- `app/build.gradle.kts` → `coreLockField("singbox", "tag")` → `BuildConfig.SINGBOX_CORE_TAG` / `SINGBOX_CORE_REPO`.
- `scripts/prepare-native.sh` → `[5/5]` stage downloads the four Android assets and installs each as `libsingbox.so` (Android only extracts `lib*.so` from `jniLibs`), then asserts the machine type per ABI and writes the sha256 manifest.
- `scripts/update-core-lock.sh` → resolves the sing-box pin on the `beta` channel, which is what the product ships: `v1.14.0-extended-2.7.1` is the newest extended build and carries no prerelease flag.
- Settings → Information prints all three tags, adds a link to each upstream, and includes sing-box in the copied version details.
- Settings → Tunnel core prints them again next to the switch that chooses between them.

`.github/workflows/verify.yml` gained a **Core versions agree everywhere** step that fails the PR if
any of the three pins is missing, if the gradle build or the native script stops reading the lock,
if the sing-box channel stops being `beta`, or if a hard-coded version string appears outside the
lock. The step's body was executed against this tree and exits 0.

## 3. The engine switch

New files, all under `com.marbleng.app.core`:

- `CoreEngine.kt` — the enum (`xray`, `singbox`), the never-failing `parseCoreEngine`, `AppSettings.coreEngine()`, and `CoreEngineInfo` (display name, real binary name, upstream repo).
- `SingBoxConfigBuilder.kt` — writes the config. Two strategies: hand the original share link to the extended core's own `parser` outbound, or translate the stored Xray JSON into explicit outbounds (chain included). `describe()` is the promise the UI can print: a node the core cannot run is refused with a reason, never guessed at.
- `SingBoxManager.kt` — owns the process: `check` before `run`, `runtime-singbox.json`, `logs/singbox.log`, `singbox-cache.db`, and the Clash API on 127.0.0.1 with a per-session secret, which is what URL test reads.
- `LatencyBufferPolicy.kt` — see below.

The data path did not change, and that is the whole design:

```
Android VpnService (TUN) → hev-socks5-tunnel → SOCKS5/127.0.0.1 → Xray-core | sing-box extended
```

`MarbleVpnService` decides once (`activeEngine = settings.coreEngine()`, before the first start
diagnostic is written) and then talks to "the core" through `coreAlive`, `coreStop()`,
`coreStartPhase`, `coreStartError`, `coreLogFile` and `coreTag`. Each of those names the Xray
manager in its `else` arm; three of them read themselves in the first draft of this change, which
compiles cleanly and then dies on the stack at connect time. `system-integrity-check.py` now asserts
the four `else xray.*` / `runCatching { xray.stop() }` arms so that class of bug cannot come back.

Xray's transport telemetry comes from MarbleNG's own Xray patch, so that block only runs when
`activeEngine == CoreEngine.XRAY` — reading it on the sing-box engine would report another engine's
numbers as this one's.

Switching engines closes a live tunnel (`setCoreEngine`), because the other core's process cannot
keep the tun, and drops URL test if it was selected, because that method is a conversation with a
running sing-box core.

### sing-box controls in Settings

Every switch on the page reaches the config the core is started with; none is decorative:

| Switch | Effect on the emitted config |
| --- | --- |
| Prefer link parser | `describe()` returns `link-parser` vs `translated`. Off, Marble translates what it can and the parser still catches everything it cannot |
| Unified delay | `experimental.unified_delay.enabled` |
| Cache file | `experimental.cache_file` is written, or omitted entirely |
| Connect timeout | per-outbound `connect_timeout`, 3–60 s |

The control API port is deliberately not a setting: Marble picks a free port and a random secret per
session, and the page says so rather than offering a knob that would only create collisions.

## 4. Three pings, not seven

The V148 list (Smart, Real test, TCP Connect, TCP+TLS gate, HTTP GET, HTTP HEAD, ICMP) is gone.
Five of those answered one question with a different socket call, so the same node reported five
numbers depending on which button was pressed; ICMP answered it with the carrier, because an Iranian
mobile link echoes a request even when the proxy behind it is dead.

`ProbeMethod` is now:

- **REAL_DELAY** — PattNG's real ping. A 1 s TCP gate first so a dead port never spends a core, then one real HTTPS round trip through the live tunnel to `DelayTest.url(settings.delayTestUrl)`. No tunnel: the gate result is returned, labelled `no-tunnel-tcp-gate`.
- **TCP_PING** — PattNG's tcping. One timed `Socket.connect` to the node's own `host:port`. The only method whose verdict belongs to the endpoint rather than the route, so it is the only one `isEndpointLevel()`.
- **URL_TEST** — sing-box extended's own delay endpoint, reached through `RouteProbe.urlTestHook`, which the repository installs once. It prefers the tunnel the user already has (`urlTestLive` when the live engine is sing-box and the profile is the active one) and otherwise measures the node with a throwaway instance (`urlTestProfile`).

URL test is offered in Settings only while sing-box extended is the selected engine; the row is
dimmed, unclickable and badged "sing-box extended only" on Xray, because selecting it there would
leave the Servers list showing a permanent "no response" that no row could explain.

Stored legacy values migrate: `TUNNEL`/`HYBRID`/`TCP_RECOMMENDED`/`HTTP_GET`/`HTTP_HEAD`/`ICMP` →
`REAL_DELAY`, `TCP_CONNECT` → `TCP_PING`. The old measurement primitives (`icmp*`, `httpPing*`,
`dnsPing*`, `tcp*`) stay in `RouteProbe` as the instrument set the three methods and the ping-truth
tests are built on; nothing in the product reaches them except through `measureUnified`.

## 5. The Xray throughput bugs

Three arithmetic errors, all of which compile cleanly and only ever appear as a slow link.

**MTU clamped by loss alone.** `if (input.tcpStressed && input.retransmitRate > 0.15 || input.lossRate > 0.15)` — `&&` binds tighter than `||`, so the condition was really "stressed and retransmitting, *or merely losing packets*". A steady 15% loss with a healthy TCP stack is the normal state of a censored mobile link — it is the reason the user runs a proxy — and it pinned MTU to 1280 for the entire session. At 1280 a 1500-MTU radio carries ~14% less payload per packet. The parentheses are now explicit and both halves of the evidence are required.

**MSS charged the IPv6 header on IPv4.** `val overhead = 60` unconditionally, and that number is written into the core's socket options, so the waste was in the socket rather than in a report. It is `60` for IPv6 and `40` for IPv4 now, with the ceiling following the family (1440 / 1460). `IranModeEngine.recommendedMtuMss` already received `hasIpv6` and dropped it on the floor; it passes it now, and `MarbleIntelligence.adaptiveMtu` takes it from the snapshot it already reads.

**The latency-first queue was a constant.** The bufferbloat guard is right — Marble must not add a second queue in front of the radio — but it clamped to 65 536 bytes regardless of the path. A socket buffer only throttles once it is smaller than the bandwidth-delay product, and at 200 ms RTT 64 KiB caps a single stream at ~2.6 Mbit/s however fast the radio is. That is the "signal is fine, everything is slow" report, and it explains why the same phone was fast on direct traffic: the kernel sizes its own buffers, and the clamp only ever applied to the proxy path. `LatencyBufferPolicy` now sizes the queue to the measured BDP — still never more than the throughput pass asked for, still the historical baseline whenever there is no rate or no believable RTT to size from.

## 6. The customizer

- **Floating button.** Home style 2's split FAB (one shutter while down; disconnect + ping once up) is now extracted as `HomeFloatingSplitControl` and offered as the modular layout's "Floating button" option. It renders as an overlay pinned bottom-end rather than as a module — a module scrolls away, and the point of the silhouette is that it stays under the thumb — and the page reserves the same 104 dp of clearance Theme 2 reserves so nothing is buried under it. One control, one implementation, so the two pages cannot drift apart.
- **Hide the Customize affordance.** The switch is in the customizer itself, at the moment the user is customizing, which is when they ask for it. `Settings → General → Home layout` carries the switch back, so hiding the row can never strand the layout. "Reset layout" in the customizer restores it too.

## Verification

`scripts/system-integrity-check.py` — **169/169**, including 17 invariants added by this change
(the three-method probe set, no production reference to a retired method, the URL-test hook, the
legacy migration, the sing-box pin/packaging/switch/version parity, the DNS `server` key, the
non-recursive engine reads, the telemetry guard, the MTU precedence and family fixes, the BDP queue,
the Home caption removal, the customizer invariants).

Also green locally: `tools/compose-scope-check.py` (0 violations), `tools/kotlin-structure-check.py`,
the Compose function-type check extracted from `verify.yml`, `bash -n scripts/*.sh`, and the new
version-parity step's shell body (exit 0 against this tree).

`scripts/v144-verify.py` reports two failures — `probePool` and `sharedUnderlay` are expected in
`AppRepository.kt` and absent. Both are absent at the branch point too, so they predate this change,
and no workflow runs that script.

**Not verified locally:** there is no JDK, Android SDK or Gradle in this environment and the
download hosts are unreachable, so `:app:compileDebugKotlin` and `:app:testDebugUnitTest` have not
been run here. The pull request's CI run is the compile and test gate for this branch.
