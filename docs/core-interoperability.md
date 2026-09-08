# Core interoperability and Android runtime contract

Reviewed against `shtorm-7/sing-box-extended v1.14.0-extended-2.7.1` and
`XTLS/Xray-core v26.7.28` on 2026-09-07. Extended's newest release is already the
version in `core-lock.json`; incrementing a version number would not fix these integration errors.

Updated 2026-09-08 for **MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157**: the Android core is no longer a
downloaded release artifact. `core-lock.json` pins a source `commit` and a local `patch` level, and
`scripts/prepare-native.sh` compiles every ABI from that commit with
`scripts/inject-singbox-android-fix.py` applied, reporting itself as
`1.14.0-extended-2.7.1-marble.crash-fix.1`. See `docs/SINGBOX_ANDROID_CLI_CRASH_V157.md`.

## Runtime and DNS

- Android `VpnService` and HEV own TUN. The core owns **one loopback mixed inbound**.
  No auto-route, automatic interface detection or enforced netlink monitor is needed.
- Avoiding a monitor request is only half of that contract: an app-UID `GOOS=android` child has
  **no** netlink interface monitor at all, and the core must survive the nil rather than dereference
  it. `protocol/direct/outbound.go` did exactly that at `StartStatePostStart` for every config
  MarbleNG writes (all of them carry a `direct` outbound), which is the crash V157 backports the
  upstream nil-guards for. `/data/system/packages.xml: permission denied` from the unconditional
  Android package-manager probe is a WARN on the same path, is survivable, and is evidence about the
  build rather than a fault in its own right.
- The standalone Android CLI's `type: local` reads `/etc/resolv.conf`. It is not SFA's
  platform DNS callback. Production starts a loopback UDP bridge, backed by
  `DnsResolver.rawQuery(network, ...)` on API 29+, or `Network.getAllByName` on API 26–28.
  The bridge selects a non-VPN network on each query and respects Android Private DNS.
  API 26–28 support A/AAAA only. Its two workers and bounded queue prevent a timeout storm
  from spawning unlimited resolver threads; older OS lookups themselves are not interruptible.
- Only proxy/provider bootstrap and explicitly direct domestic queries use that bridge.
  General DNS uses a **real `fallback` transport**, with at most three encrypted peers,
  evidence order retained, and one overall sequential timeout. It cannot fall through to
  plaintext underlay DNS. An underlay resolver failure does not prove a tunneled resolver failure.
- DNS servers use typed 1.14 options. Every dial has a default resolver. Query-time GeoIP
  address filters, `independent_cache`, `store_rdrc` and deprecation escape hatches are gone.
- Required Iranian GeoIP/geosite and ad-block SRS files are small, pinned build-time APK
  assets. `singbox-rules-lock.json` fixes upstream commit, byte lengths and SHA-256 hashes.
  Startup extracts them atomically and verifies them; it never downloads from GitHub.
  A damaged/incomplete APK reports `core-assets`, not a failed server.

## Conversion boundaries

The original profile is not overwritten. Connect and measurements share conversion and core
validation. Stored JSON takes precedence over a share link: Extended's URI parser does not
preserve all XHTTP extra fields or subsequent user edits. Link-only profiles still use the
Extended parser (including TUIC/AnyTLS). Native sing-box outbound graphs can be imported.

Forward translation covers the implemented VLESS, VMess, Trojan, Shadowsocks, SOCKS, HTTP and
Hysteria forms. It preserves `streamSettings.method`, per-field credential aliases, REALITY
`password`/`publicKey`, short ID, uTLS, XHTTP padding/extras/xmux/download settings, WS headers,
mKCP options and representable transport/security options. Detours point from the entry to the
outer hops. Missing hops and cycles are configuration errors, not silently truncated chains.
Xray's `protocol: hysteria` with `version: 2` remains Hysteria2, including Salamander.

Reverse translation supports the implemented common native outbounds (VLESS, VMess, Trojan,
Shadowsocks without plugins, SOCKS5, HTTP, Hysteria2) and their representable transports/chains.
The Xray hardener invokes it for connect, temporary tests and integrated rank.

**The union of both cores' features is not an intersection.** Examples reported explicitly:

- Xray full-certificate pins cannot be relabelled as sing-box SPKI pins or dropped.
- Xray Mux.Cool is not smux/h2mux/yamux. Optional imported Xray mux is disabled with a note;
  it is never replaced by an incompatible server protocol.
- Xray gRPC multi-mode, legacy encrypted QUIC camouflage, freedom fragment/noise detours,
  unsupported ECH/verification options, SIP003 plugins and unknown wire fields are not guessed.
- Core-exclusive native endpoints, selectors, TLS policies or protocols without an exact Xray
  implementation report `config-unsupported`, with the field path, when Xray is selected.
- REALITY authentication is preserved, but Xray's `spiderX` crawling has no implementation in
  this core; the build notes expose that limitation.
- Marble owns local inbounds, DNS and routing settings for **both** engines. Native imports
  retain proxy graphs and their relevant resources, not arbitrary external TUN/listener policies.
  Managed geo rules use the bundled ir/private/ads data; other tags require Xray currently.

These are actionable compatibility errors. Silently producing a JSON document that passes a
shape test while changing authentication or transport would be worse than rejecting it.

## Measurement truth and resource ownership

- Real Delay must have an actual selected-core tunnel. A raw 1-second TCP precheck can neither
  veto it nor count as its result. TCP Ping remains an explicitly endpoint-only method.
- Benchmarks, tuner checks, fingerprint verification and temporary subscription fetches share
  the selected-core temporary execution contract. Sing-box rank does not run the integrated
  Xray rank helper. A live sing-box SOCKS listener can be borrowed for subscription fetches.
- URL Test uses sing-box's authenticated local Clash controller when that engine is selected.
  On Xray it performs verified HTTPS HEAD through the selected live/temporary SOCKS route;
  it does not switch to sing-box or remove the user's selected probe method. It requires HTTPS (the pinned
  API otherwise silently substitutes gstatic), a positive delay, and a signed-16-bit-safe timeout.
  It tries reference URLs lazily inside one per-profile process and deadline, not an eager
  `map` that spawns/checks every core after the first success. 401 is not API readiness.
- Temporary sing-box processes are limited to two. Each has unique config/check/log files,
  ports and controller secret, with no shared cache database. Cancellation awaits process
  termination and releases resources. Logs and diagnostic reads are bounded. Go's 96 MiB
  `GOMEMLIMIT` is a soft GC target, **not** proof that Android cannot kill a process.
- Real Delay preserves the configured HTTPS URL, including port and query. Its sockets have a
  wall-clock cancellation/deadline guard, not just a read timeout that can be repeatedly reset.
- Core installation/configuration/asset failures do not decrement server health, launch a
  failover walk, or silently latch another engine. The kill switch still holds where possible.
  LOW_MEMORY/package replacement/user-requested exit records alone do not establish a memory leak.
- A core that **cannot start on this device** (a Go panic, the netlink ban) is one fault, not one
  fault per node. `SingBoxCoreSelfTest` asks the binary once per installed APK with an offline canary
  — a local `mixed` inbound plus a `direct` outbound, the exact path that crashed — and refuses to
  spawn further children when the verdict is a core fault. `ProbeLocalFaultGate` stops a sweep on the
  first such result, keeps the measurements already made, and the batch summary says the device could
  not measure instead of printing `0 of N reachable`. Per-node refusals, capacity, slowness and every
  network-shaped reason deliberately do **not** stop a sweep: those are its subject matter. Engine
  selection is still never switched automatically.

## Verification

Local source checks:

```sh
python3 tools/kotlin-structure-check.py
python3 scripts/system-integrity-check.py
bash -n scripts/*.sh
python3 scripts/prepare-singbox-rules.py --verify-only
python3 -m py_compile scripts/inject-singbox-android-fix.py
```

Core-source checks (the Android half of the contract cannot be proven by a Linux artifact):

```sh
git clone https://github.com/shtorm-7/sing-box-extended && cd sing-box-extended
git checkout "$(jq -r '.singbox.commit' /path/to/core-lock.json)"
python3 /path/to/scripts/inject-singbox-android-fix.py .   # idempotent; exits 1 on anchor drift
CGO_ENABLED=0 go test ./protocol/direct ./route            # the injected nil-monitor regressions
```

`scripts/prepare-native.sh` runs both before it builds any ABI, and `.github/workflows/verify.yml`
runs them again on every push as the `sing-box Android CLI crash-fix native smoke` step. The host
acceptance binaries from `scripts/prepare-native-test-cores.sh` are the **unpatched** `linux-amd64`
release on purpose: on Linux a real interface monitor exists, so the crash path cannot be exercised
there — which is exactly why the Go tests stub the monitor instead.

Real-core acceptance (Linux x86-64, JDK 17, Android compile SDK and Gradle installed):

```sh
bash scripts/prepare-native-test-cores.sh
# Export the two binary paths printed by the script, then:
MARBLE_NATIVE_TESTS_REQUIRED=1 gradle :app:testDebugUnitTest :app:compileDebugKotlin :app:compileReleaseKotlin
```

The proposed `verify.yml` update installs the pinned cores and makes native tests mandatory.
It requires the GitHub connection's workflow-write permission (see the PR's validation status).
Without the two binary paths, native tests explicitly skip; they must not be counted as passes.
Tests validate generated
configs **and** run offline loopback traffic through the actual cores: VLESS/XHTTP/REALITY with
the reported password/extra shape, URL Test, verified TLS Real Delay, wrong-account negative
control, encrypted DNS fallback, simultaneous sessions and cancelled startup. Shape-only tests
cannot certify network reachability. Android runtime smoke and physical-network conditions must
still be distinguished from Linux loopback evidence in release reports.

## Authoritative references

- [Pinned Extended release](https://github.com/shtorm-7/sing-box-extended/releases/tag/v1.14.0-extended-2.7.1)
- [Upstream nil-interface-monitor fix backported by V157](https://github.com/SagerNet/sing-box/commit/288411b0b9044c11a00a8ab478000e3ec1133101)
- [The crashing call site: `direct` outbound `fetchMyAddresses`](https://github.com/shtorm-7/sing-box-extended/blob/v1.14.0-extended-2.7.1/protocol/direct/outbound.go)
- [The unconditional Android package-manager probe](https://github.com/shtorm-7/sing-box-extended/blob/v1.14.0-extended-2.7.1/route/network.go)
- [Identical panic reported against the official Android CLI](https://github.com/SagerNet/sing-box/issues/4498)
- [Extended transport schema](https://github.com/shtorm-7/sing-box-extended/blob/v1.14.0-extended-2.7.1/option/v2ray_transport.go)
- [Extended TLS schema](https://github.com/shtorm-7/sing-box-extended/blob/v1.14.0-extended-2.7.1/option/tls.go)
- [Extended local DNS system configuration](https://github.com/shtorm-7/sing-box-extended/blob/v1.14.0-extended-2.7.1/dns/transport/local/systemconfig/source_resolv.go)
- [Extended DNS fallback](https://github.com/shtorm-7/sing-box-extended/tree/v1.14.0-extended-2.7.1/dns/transport/fallback)
- [Clash delay API](https://github.com/shtorm-7/sing-box-extended/blob/v1.14.0-extended-2.7.1/experimental/clashapi/proxies.go)
- [1.14 migration](https://sing-box.sagernet.org/migration/)
- [Pinned Xray transport configuration](https://github.com/XTLS/Xray-core/blob/v26.7.28/infra/conf/transport_method.go)
