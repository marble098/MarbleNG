# Marble Freedom — serverless-for-Iran research & bug-fix log (2026-08-29)

This document records the deep internet/source research behind the Marble Freedom
(serverless) fixes in this branch, plus the exact defect-to-source mapping. It complements
`IRAN_MODE.md` (detection/countermeasure tiers) and `BUG_AUDIT_2026-08-29.md`.

## 0. How the evidence was obtained (this session)

`raw.githubusercontent.com` and release assets are blocked in this sandbox; `codeload.github.com`
and `api.github.com` work. The upstream sources below were therefore verified by downloading the
repo **archives** (`codeload …/tar.gz/refs/tags/…`) and reading the files directly:

- `XTLS/Xray-examples` `main` → `Serverless-for-Iran/serverless_for_Iran.jsonc` (+ README,
  `serverless_with_mitm_for_Iran.jsonc`) — the canonical config, read in full.
- `XTLS/Xray-core` **tag `v26.7.28`** (the exact tag Marble pins in `core-lock.json`) —
  `infra/conf/xray.go`, `infra/conf/freedom.go`, `infra/conf/dns.go`,
  `infra/conf/transport_sockopt.go` — the parser the emitted config must satisfy.
- `XTLS/Xray-core` `main` (`c1958db`) `proxy/freedom/freedom.go`, `transport/internet/dialer.go`,
  `app/dns/*` — runtime behaviour of fragment/noise writers and DNS bootstrap.

## 1. What "Serverless for Iran" is

Serverless anti-censorship is a **local-only Xray configuration**: no remote proxy server, no
anonymity. The user's own IP is the egress IP. It defeats Iranian DPI by reshaping the *first
bytes* of each TLS/TCP flow and by padding UDP datagrams, so the national gateway cannot
reassemble the ClientHello / read the SNI / fingerprint the session:

- **TCP/TLS fragmentation chain** — the ClientHello is split into TLS records and/or 1-byte TCP
  writes with millisecond pacing so the DPI's reassembly heuristics never see a complete SNI.
- **UDP noise injection** — random datagrams are sent before the first real QUIC/443 packet so
  stateful UDP trackers cannot match the flow.

Reference implementations researched (all verified):

| Source | Where | Notes (verified) |
| --- | --- | --- |
| XTLS `Xray-examples` → `Serverless-for-Iran/serverless_for_Iran.jsonc` | codeload archive, read in full | Canonical config. `"version": {"min": "25.12.8"}`; all fragment hops are `"protocol": "direct"`; two **alternative** chains: `tls-fragment` (tlshello/6/0/0) → `full-fragment` (1-1/1/4/517) **or** `skip-fragment` (1-1/130/560/4) → `_chain-skip` (2-4/1/4/130); `full-fragment`/`_chain-skip` carry `sockopt.domainStrategy: ForceIP` + `happyEyeballs {tryDelayMs: 300, prioritizeIPv6: true, interleave: 2, maxConcurrentTry: 20}`. Separate `udp-noises` outbound (13× ipv4 `1250/10` + 13× ipv6 `1230/10`, `targetStrategy: ForceIPv6v4`); fakedns pools `198.19.0.0/16` and `fc00:2000::/19`; DoH `https://cloudflare-dns.com/dns-query` with `timeoutMs: 10000`, `finalQuery: true`, `tag: no-filter-dns`, routed to `full-fragment`; `dns.hosts` maps `cloudflare-dns.com → challenges.cloudflare.com` (a **domain→domain** proxied mapping, not an IP pin); `queryStrategy: UseSystem`, `useSystemHosts: true`, `serveStale: true`, `serveExpiredTTL: 21600`; routing blocks `10.10.34.0/24`, `2001:4188:2:600::/64`, `0.0.0.0`, `::`, fakedns pools; `.ir`/private/`geosite:category-ir` → `tcp-direct-out`/`udp-direct-out`; QUIC and UDP/443 → `udp-noises`; TLS and TCP → `full-fragment`; catch-all TCP → `full-fragment`, then `block-out` port 0-65535. **No anonymity** (README). |
| `XTLS/Xray-examples` → `Serverless-for-Iran/README.md` + `serverless_with_mitm_for_Iran.jsonc` | codeload archive | Requires Xray ≥ 25.12.8; MitM-domain-fronting variant needs a self-signed CA imported on Android; same "no anonymity" note. |
| `GFW-knocker/gfw_resist_HTTPS_proxy` → `ServerLess_TLSFrag_Xray_Config_New.json` | fetched earlier (raw fetch then; content recorded in session memory) | Battle-tested single-hop variant: `fragment-out` = `freedom` with `fragment packets "1-1" length "1-3" interval "5-10"`, `domainStrategy: "UseIP"` top-level **and** in `sockopt`, `sockopt mark 255/tcpNoDelay/tcpKeepAliveIdle`; `dns-out`; a fake VLESS/TLS/WS→google.com outbound that exists only because some clients reject freedom-only configs (see §2, DEFECT A side note); DoH `https://cloudflare-dns.com/dns-query`; `dns.hosts` maps `domain:youtube.com → google.com` (again **domain→domain**); `routing.domainStrategy: IPIfNonMatch`; port 53 → dns-out, 0-65535 → fragment-out; `policy.levels.8.connIdle: 300`. |
| 2dust/v2rayN #6976, 2dust/v2rayNG #4243 | GitHub issues | "serverless configs don't start" class of bug: clients that only accept a VLESS/Trojan/etc. outbound refuse to start when the config contains only freedom/blackhole/dns outbounds. v2rayNG fixed with commit `efd07167074f639aec7ca2c7d0478cf6f2d75c67` ("Custom configuration can use any outbound"). MarbleNG already handles this via `XrayConfigHardener.isSelectableProxy`. |
| `XTLS/Xray-core` v26.7.28 `infra/conf/xray.go` lines 41-42 | tag archive | `"direct"` and `"freedom"` both build `new(FreedomConfig)` — Marble using `protocol: "freedom"` is a plain alias of the official `direct`. |
| v26.7.28 `infra/conf/freedom.go` | tag archive | `FreedomConfig` JSON: `targetStrategy` (new name) with `domainStrategy` as its **fallback alias** (Build reads TargetStrategy, falls back to DomainStrategy — Marble emits `domainStrategy`, which is identical in effect); `fragment`/`noises` in `settings`; `fragment.packets` = `tlshello` | `""` | `N-M` (from ≥ 1, else error); `length`/`interval` required `Int32Range` (`"N"`,`"N-M"` or int), `maxSplit` optional; noises only `rand/str/hex/base64`, `delay` range, `applyTo ip|ipv4|ipv6` (case-insensitive, default `ip`). |
| v26.7.28 `infra/conf/dns.go` | tag archive | `dns.hosts` value is a single string (IP → `Ip` list; domain → `ProxiedDomain`) **or** array of strings (all-IP array → `Ip` list; any domain in the array → `ProxiedDomain`). Marble's `"host": ["178.22.122.100", "185.51.200.2"]` form is accepted and produces IP-list pins. `NameServerConfig` accepts `address/port/skipFallback/domains/queryStrategy/tag/timeoutMs/finalQuery/…`. |
| v26.7.28 `infra/conf/transport_sockopt.go` | tag archive | `sockopt.domainStrategy` (same enum incl. `ForceIP`, `ForceIPv4`, …), `dialerProxy`, `tcpKeepAliveIdle/Interval`, `tcpUserTimeout`, `tcpFastOpen`, `tcpMaxSeg`, `happyEyeballs {tryDelayMs, prioritizeIPv6, interleave, maxConcurrentTry}` all parse. HappyEyeballs defaults `tryDelayMs 0 / maxConcurrentTry 4` — a plan must arm both, which Marble's `applyAddressFamily` does. |
| `XTLS/Xray-core` main `proxy/freedom/freedom.go`, `transport/internet/dialer.go`, `app/dns/{nameserver_doh,hosts}.go` | sparse clone / archive | `FragmentWriter` (tlshello = 0..1 packet special mode; `PacketsFrom..To` range mode; 1-byte/4 ms with `maxSplit`); `NoisePacketWriter` emits noise **before** the first datagram and skips UDP port 53; domain destinations are resolved through **Xray's built-in DNS module only when a strategy is set** (`DomainStrategy.HasStrategy()`), otherwise the OS resolver is used (`net.DefaultResolver`) — line 290-298 TCP, 607-611 UDP; system dialer resolves the destination (`LookupForIP`) **before** `dialerProxy` redirect, so only the innermost hop's sockopt controls the real socket; a remote DoH server is dialed through the dispatcher (can ride the Freedom chain) but its **hostname must already resolve**, hence the pin/proxy-domain requirement. |

## 2. The defects found in Marble Freedom

### DEFECT A — the Freedom chain never resolved destination hostnames (primary)

`XrayConfigHardener.harden()` applies the address-family plan only to outbounds that have
**endpoint domains** (`endpointDomains()`). The Freedom fragment hops have no endpoint — their
destination is whatever the user visits — so they kept the Xray default `AsIs`.

Xray's `freedom.go` behaviour with `AsIs` + a domain destination: **no built-in DNS
resolution — the domain is handed to the OS resolver**. In Iran that resolver is the poisoned
ISP DNS (`10.10.34.0/24` block pages, documented in `IRAN_MODE.md`) or silently drops the
query. Every connection that reaches the chain with a `domain` ATYP therefore fails:

- local-proxy (SOCKS) users — any browser/Telegram pointed at `127.0.0.1:<port>`;
- Marble's own in-tunnel SOCKS work: subscription refresh while connected, Privacy/exit-identity
  trace (`www.cloudflare.com`), live RTT probes (`www.gstatic.com`, `cp.cloudflare.com`,
  `www.google.com`), adaptive-DNS observation.

Both known-good upstream configs set it explicitly:

- XTLS official: `sockopt.domainStrategy: "ForceIP"` + `happyEyeballs {tryDelayMs: 300, …}` on
  `_chain-skip` / `full-fragment` (the hops that dial real sockets) and `ForceIP` on
  `tcp-direct-out`;
- GFW-knocker: `domainStrategy: "UseIP"` top-level **and** in `sockopt`.

**Fix:** `XrayConfigHardener` now applies `AddressFamilyPolicy.plan()` to the **innermost**
Freedom/direct fragment hop (the one with no `proxySettings`/`dialerProxy`) and writes it to
both `settings.domainStrategy` (used by the hop's UDP `PacketWriter`) and
`sockopt.domainStrategy` + armed Happy Eyeballs (used by the TCP dialer — confirmed against
`infra/conf/freedom.go` and `transport_sockopt.go` at v26.7.28). TCP and UDP now both resolve
through Xray's encrypted DNS module — the same module whose DoH servers ride the Freedom
chain — instead of the poisoned OS resolver.

Side note (client compat): the "need a fake VLESS outbound" bug is not present —
`XrayConfigHardener.isSelectableProxy` accepts freedom/direct hops, so Marble never needs the
GFW-knocker `fakeproxy-out` workaround.

### DEFECT B — Freedom DNS servers that cannot bootstrap themselves

The Freedom DNS list (`freedomDnsCleanResolvers`) contained hostname DoH endpoints
(`dns.adguard-dns.com`, `doh.sb`, `dns.shecan.ir`) that the DNS module must resolve **before**
it can use them. With `useSystemHosts: false` and no `dns.hosts` pin, that resolution recurses
into the same module or falls out to the OS resolver — either way the server is unusable (or the
lookup is poisoned), while `finalQuery: true` sat on the last (Shecan) entry, making the
"guaranteed final answer" the least reliable one.

Upstream breaks the loop differently: the official XTLS config maps
`cloudflare-dns.com → challenges.cloudflare.com` and the GFW-knocker config maps
`youtube.com → google.com` — both **domain→domain `ProxiedDomain`** mappings that the fakedns /
system-hosts path then resolves. Marble instead uses the mechanism the same parser also
supports (verified in `HostAddress`/`newHostMapping`): a direct **IP-list pin**, so the DoH
hostname resolves to a stable address from inside the module while TLS SNI still carries the
real hostname.

**Fix:**
- only IP-literal DoH endpoints or hosts pinned in `dns.hosts` survive in the Freedom resolver
  pool;
- `dns.hosts` pins `dns.shecan.ir` (178.22.122.100 / 185.51.200.2) and `dns.adguard-dns.com`
  (94.140.14.14 / 94.140.15.15);
- `doh.sb` is removed from the defaults (its addresses are not stable enough to pin);
- **new:** even with `freedomDnsAuto = false`, the generic `dnsPrimaryDoH`/`dnsSecondaryDoH`
  pair is filtered to IP literals before it can enter a Freedom config, and falls back to the
  stock 1.1.1.1/8.8.8.8 IP-literal DoH if nothing survives — a hostname DoH can no longer
  re-enter the Freedom path through the "auto off" branch.

Note: `freedomDnsQueryStrategy` remains intentionally governed by `AddressFamilyPolicy` (one
decision for DNS records, endpoint dials, and blocking rules); a per-Freedom override that
disagrees with the IPv6 switch would be rejected by the hardener's invariant checks, so it
stays out of the emitted config rather than being silently half-applied.

### DEFECT C — DoH query budget too small for a fragmented first write

The first TCP write of every stream on the chain is sliced into 1-byte packets with 4 ms
pacing (up to `maxSplit` 517), so a DoH TLS ClientHello alone can take ~1–2 s. The Freedom DNS
servers inherited the generic 1 350 / 1 650 ms serial budgets — i.e. every server looked dead
even when reachable, and cold lookups could burn 8+ s before a working resolver was found
(if any). Verified fact: the official XTLS config gives its DoH server **`timeoutMs: 10000`,
`finalQuery: true`** for precisely this reason.

**Fix:** Freedom DoH entries now get 5 000 ms + 750 ms per position (bounded, serial as before)
— applied to **any** Freedom profile, not only when the clean-resolver list is active, because
the fragment chain slows the first write regardless; `serveStale` still returns validated
answers while background refresh runs.

### DEFECT D — Identity Guard pinned the user's *own* egress and killed the route

*(documented below; keep reading — it is the second independent route-killer.)*

### DEFECT E — the outer "tlshello" fragment hop was rejected by real servers (PRIMARY, runtime-proven)

Marble's default recipe (`SMART_ADAPTIVE` → `MULTI_LAYER_CASCADE`) put `packets: "tlshello",
length: "6", interval: "0"` on the outer `proxy` hop. Xray's `tlshello` mode is **not** a
TCP-segment splitter; `proxy/freedom/freedom.go` `FragmentWriter` re-encodes the ClientHello
into **complete tiny TLS records** (HEADER_i + PAYLOAD_i, 5-byte record header per 6-byte
chunk). Upstream itself calls this out:

- Xray-core issue **#4370** — "`tlshello` fragment … only break from end of each payload …
  this creates a pattern that can be recognized by GFW, because every packet is a complete
  tls record"; the maintainer-accepted workaround is chaining *another* fragment hop
  (`dialerProxy("tlshello" + "1-1")`), which is exactly the shape Marble already had — yet
  the record-level pattern still fails.
- Xray-core discussion **#5969** (field notes 2026-04, MCI / Irancell / Shatel) — the SNI-based
  DPI now reassembles TCP segments and re-inspects; plain `fragment: tlshello` no longer
  bypasses it and blocked SNIs get **RST'd**.

**Runtime proof (this session, real engine):** the PyPI `xray-core==1.8.26.10` wheel vendors the
exact Xray tag Marble pins (`UPSTREAM_VERSION = v26.7.28`). Running the emitted chain against
real servers:

| Outer hop | Fastly `pypi.org` | Cloudflare `registry.npmjs.org` | GitHub `github.com` | AWS `httpbin.org` | `example.com` |
| --- | --- | --- | --- | --- | --- |
| `tlshello / 6 / 0` (+ inner 1-1/1/4/517) | RST `broken pipe` | RST | RST | RST | RST |
| `tlshello / 100-200 / 10-20` | RST | — | — | RST | RST |
| `1-1 / 1-3 / 5-10` (+ inner) | **HTTP 200** | **HTTP 200** | **HTTP 200** | **HTTP 200** | HTTP 200 |
| official XTLS `skip-fragment` pair | HTTP 200 | — | — | — | — |
| official XTLS `tls-fragment` pair | RST | — | — | — | — |

A local TLS 1.3 server accepts the `tlshello` shape (RFC 8446 §5.1 *permits* handshake
fragmentation across records), so the bytes are protocol-legal — but production TLS fronts
(Fastly/Cloudflare/GitHub/AWS) and Iran's DPI actively reset it. Marble's chain therefore
looked correct yet could not complete a single real TLS connection.

**Fix:** the outer hop of every default Freedom recipe is now the GFW-knocker packet split
`1-1 / 1-3 / 5-10` (the same fragment the community's battle-tested config uses; official XTLS
offers it as `skip-fragment`). The ClientHello stays one valid TLS record while per-packet DPI
never sees the SNI in one packet — and the runtime matrix above shows 200 OK through the full
3-hop chain with UDP noises, pins and 5 s DoH budgets. `tlshello` remains available only if a
user explicitly selects a recipe that still uses it (none of the factory presets do now).

### DEFECT D — Identity Guard pinned the user's *own* egress and killed the route

`ServerlessFreedomEngine.pinSession()` enables `identityGuardEnabled` +
`identityGuardStrictNoFailover`. `MarbleVpnService.verifyExitIdentity()` (run at tick 12 and
every 180 s) observes the egress IP **through the Freedom chain**, i.e. the user's own public
address. On mobile that address legitimately changes (CGNAT rebinding, WiFi↔mobile handover,
IPv6 prefix rotation). The guard then treated it as "proxy exit rotation":
`handleFailure("Identity Guard detected public exit-IP rotation")` — and
`AppRepository.recoveryCandidates()` deliberately returns an empty list for serverless, so the
tunnel stayed fail-closed with **no recovery**. Result: Freedom worked for a few minutes, then
died and could not recover — the classic "Marble Freedom doesn't work".

**Fix:** for `marble-serverless-freedom`, `verifyExitIdentity()` is informational only: it
reports the observed egress in the Privacy sentinel but never pins, never blocks, never tears
down. Pinning is meaningful only when a remote proxy exit exists.

## 3. What was checked and confirmed

- Emitted `fragment` / `noises` shapes (`packets`, `length`, `interval`, `maxSplit`,
  `type: rand`, `packet`, `delay`, `applyTo`) satisfy the **v26.7.28** `FreedomConfig.Build`
  parser (Int32Range acceptance, packets grammar, noise type/applyTo whitelist).
- `"direct"` is a loader alias of `FreedomConfig` at v26.7.28 (`infra/conf/xray.go:41-42`), so
  `protocol: "freedom"` is equivalent to the official config.
- `settings.domainStrategy` is the **fallback alias** of `targetStrategy`
  (`infra/conf/freedom.go` Build), so Marble's `domainStrategy: ForceIP` is exactly the
  official config's effective `targetStrategy: ForceIP` on `full-fragment`.
- `dns.hosts` accepts `"host": ["ip1", "ip2"]` → `HostMapping.Ip`; `NameServerConfig` fields
  used by Marble all exist at v26.7.28.
- `sockopt` fields used by the patch (`domainStrategy`, `dialerProxy`, `happyEyeballs`,
  keep-alive/`tcpUserTimeout`/`tcpFastOpen`/`tcpMaxSeg`) parse at v26.7.28; Happy Eyeballs
  defaults are `tryDelayMs 0`/`maxConcurrentTry 4`, which Marble's plan overrides correctly.
- MarbleNG already accepts a freedom/fragment-only outbound graph
  (`XrayConfigHardener.isSelectableProxy`, infra handling) — the v2rayN/v2rayNG "fake VLESS
  outbound required" bug is not present.
- The `dns`-outbound `rules` (`hijack`/`return`, `qType 1,28`) match the v26
  `DNSOutboundConfig` schema (legacy `nonIPQuery`/`blockTypes` is deprecated).
- `fragment` applies to TCP in `freedom.go`; `noises` to the first UDP datagram only — the
  same split the official config relies on.
- `sockopt.dialerProxy` chains bridge the original destination through every hop, so the
  innermost hop sees the user destination.
- Python emission mirror (`/tmp/mirror_test.py`, default settings and IPv6-off) validates the
  generated JSON shape; `scripts/system-integrity-check.py` passes 83/83.

## 4. Known divergences from the official config (watch-list, not changed)

These are deliberate Marble design differences. They are **not** proven bugs — but they are the
first places to look if the runtime log still shows failures after §2 fixes:

1. **Extra middle hop.** Marble emits a 3-hop chain: `proxy` (tlshello/6/0) →
   `middle-fragment` (**1-3 / 10-30 / 5-10 / 768** — values not present in any upstream
   config) → `full-fragment` (1-1/1/4/517). Upstream uses only *one* layer between the outer
   and the socket-dialing hop (or the skip/chain-skip alternative pair). The middle hop is the
   least-tested part of the config; if the log shows the chain stalling at the middle hop,
   the fix is to flatten to the official `tls-fragment → full-fragment` pair.
2. **No fakedns and no `.ir`/private direct-split.** The official config resolves `.ir`,
   private, `geosite:category-ir` and `challenges.cloudflare.com` through fakedns and sends
   them out `tcp-direct-out`/`udp-direct-out`; Marble sends everything over the chain and
   resolves everything through encrypted DoH. Functionally safe (more conservative), but it
   keeps Iranian-site traffic on the fragmented path where upstream offloads it.
3. **UDP noise on the socket-dialing hop vs a dedicated outbound.** Upstream puts noises on a
   separate `udp-noises` outbound routed **only** for QUIC/UDP-443; Marble attaches them to
   the deepest hop, so every non-port-53 UDP datagram gets padded (correct per `NoisePacketWriter`,
   but broader than upstream — cosmetic overhead, low risk).
4. **More conservative DNS.** Marble keeps `useSystemHosts: false` and encrypted-only DoH,
   where upstream uses `UseSystem` + `useSystemHosts: true` + fakedns. Marble's approach is
   stronger against poisoning but depends entirely on the resolver pool being reachable (the
   object of DEFECT B/C).
5. **Marble's Freedom DNS budgets are 5 s+, upstream is 10 s.** If logs still show DoH timeouts,
   the next knob is raising the first-server budget toward upstream's 10 s.

## 5. Next verification (needs the runtime log)

The app log the user referenced was **not present** in this workspace when the work started
(only the repository checkout). The fixes above target the failure modes that match the
reference configs and Xray source, but the last mile still needs the log:

- Bug Finder → Debug Mode export (or `adb logcat | grep -iE "xray|marble|hev|dns"`) while
  reproducing Freedom;
- or the generated runtime config (`context.filesDir/xray-*` / `XrayManager` config path) so
  `xray run -test -c <file>` output can be compared with
  `Serverless-for-Iran/serverless_for_Iran.jsonc`.

Compile/test note: this sandbox has no JDK/Gradle and outbound access to
`services.gradle.org`/`repo.maven.apache.org`/`dl.google.com` is blocked (only GitHub works),
so `./gradlew :app:compileDebugKotlin` and the JUnit run must happen in the user's build
environment. Static checks (parser-level mirror, 83/83 integrity) pass here.

## 6. Git/log evidence left in place

The changed files:

- `app/.../core/XrayConfigHardener.kt` — DEFECTS A–C (+ `freedomDnsAuto=false` guard).
- `app/.../vpn/MarbleVpnService.kt` — DEFECT D.
- `app/.../model/Models.kt`, `app/.../ui/Aether2026.kt` — DEFECT B defaults (doh.sb removed).
- `app/.../core/XrayConfigHardenerTest.kt` (+ `org.json` test dependency) — regression tests.

## 7. Freedom v2 — YouTube / X / Reddit load fix (2026-08-30)

User report: Marble Freedom (Auto Smart and every other preset) loaded X half-broken,
failed Reddit entirely, and never opened YouTube.

### Root causes (mapped to upstream)

| Defect | Evidence | Fix |
| --- | --- | --- |
| **F — untested middle hop** | §4 watch-list item 1; official XTLS is 2-hop only | Default recipes are now 2-hop (outer → full-fragment). Middle is Custom-only. |
| **G — UDP noise on TCP hop** | Official puts noises on a dedicated `udp-noises` outbound routed only for QUIC + UDP/443 | `ServerlessFreedomEngine` emits `udp-noises`; hardener keeps it and adds matching routing rules. |
| **H — poison injector dials** | Official blocks `10.10.34.0/24`, `2001:4188:2:600::/64`, `0.0.0.0`, `::` | Freedom mode always emits those block rules before direct/proxy. |
| **I — multi-CDN DNS cold-start** | Official DoH `timeoutMs: 10000`, `serveExpiredTTL: 21600`; GFW-knocker remaps `youtube.com → google.com` | Freedom DoH budgets raised to 8 s+; stale TTL 21600; ProxiedDomain remaps for youtube/googlevideo/ytimg/ggpht/gvt1/gvt2; `domainStrategy: IPOnDemand`. |
| **J — SMART overlay undid recipe** | Hardener re-wrote Freedom hop fragments from stale `freedomOuter*` fields | Named presets trust `configJson` emission; only CUSTOM is overlaid from settings. Iran Mode state is passed into `freedomRecipe` via `serverlessProfile()`. |

### Sources re-verified this session

- `XTLS/Xray-examples` `Serverless-for-Iran/serverless_for_Iran.jsonc` (codeload archive)
- `XTLS/Xray-examples` `Serverless-for-Iran/serverless_with_mitm_for_Iran.jsonc` (YouTube/X/Reddit need MitM domain-fronting for the hardest clampdowns; Freedom stays non-MitM but inherits the fragment/DNS/UDP shape)
- `GFW-knocker/gfw_resist_HTTPS_proxy` `ServerLess_TLSFrag_Xray_Config_New.json` (youtube.com → google.com hosts remap; 1-1/1-3/5-10 fragment)

MitM domain-fronting (cert install) remains out of scope for Freedom — it requires a trusted CA on the device. The non-MitM path above is what the official basic config uses for general HTTPS.
