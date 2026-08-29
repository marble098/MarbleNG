# Marble Freedom — serverless-for-Iran research & bug-fix log (2026-08-29)

This document records the deep internet/source research behind the Marble Freedom
(serverless) fixes in this branch, plus the exact defect-to-source mapping. It complements
`IRAN_MODE.md` (detection/countermeasure tiers) and `BUG_AUDIT_2026-08-29.md`.

## 1. What "Serverless for Iran" is

Serverless anti-censorship is a **local-only Xray configuration**: no remote proxy server, no
anonymity. The user's own IP is the egress IP. It defeats Iranian DPI by reshaping the *first
bytes* of each TLS/TCP flow and by padding UDP datagrams, so the national gateway cannot
reassemble the ClientHello / read the SNI / fingerprint the session:

- **TCP/TLS fragmentation chain** — the ClientHello is split into TLS records and/or 1-byte TCP
  writes with millisecond pacing so the DPI's reassembly heuristics never see a complete SNI.
- **UDP noise injection** — random datagrams are sent before the first real QUIC/443 packet so
  stateful UDP trackers cannot match the flow.

Reference implementations researched:

| Source | Where | Notes |
| --- | --- | --- |
| XTLS `Xray-examples` → `Serverless-for-Iran/serverless_for_Iran.jsonc` | GitHub raw + deepwiki | Canonical config; `"version.min": "25.12.8"`; `fragment` on `direct` (loader alias of `freedom`), `noises` on a standalone `udp-noises` outbound, fakedns pools, routing per protocol/port. |
| XTLS `Serverless-for-Iran/README.md` | GitHub raw | Requires Xray ≥ 25.12.8; MitM-domain-fronting variant needs a self-signed CA imported on Android; **no anonymity**. |
| `GFW-knocker/gfw_resist_HTTPS_proxy` `ServerLess_TLSFrag_Xray_Config_New.json` | GitHub raw | Battle-tested single-hop variant: `freedom` + `domainStrategy: "UseIP"` (top-level *and* sockopt), fragment `1-1 / 1-3 / 5-10`, **`dns.hosts` pins `cloudflare-dns.com` → concrete IPs**, `routing.domainStrategy: IPIfNonMatch`. |
| 2dust/v2rayN #6976, 2dust/v2rayNG #4243 | GitHub issues | "serverless configs don't start" class of bug: clients that only accept a VLESS/Trojan/etc. outbound refuse to start when the config contains only freedom/blackhole/dns outbounds. v2rayNG fixed by making custom configs accept any outbound. MarbleNG already handles this via `XrayConfigHardener.isSelectableProxy`. |
| XTLS/Xray-core `infra/conf/freedom.go`, `infra/conf/xray.go` | Source at v25.12.8 / main / v26.7.28 | `"direct"` is an alias of `FreedomConfig`; `fragment`/`noises` live in `settings`; only `rand/str/hex/base64` noise types, `applyTo ip/ipv4/ipv6`, `fragment.packets tlshello|N-M`, `length`/`interval`/`maxSplit` ranges; `domainStrategy` accepts `ForceIP` etc. |
| XTLS/Xray-core `proxy/freedom/freedom.go` | Source | `FragmentWriter` (`tlshello` = 0..1 packet special mode; `PacketsFrom..To` range mode, 1-byte/4 ms with `maxSplit`); `NoisePacketWriter` sends noise **before the first datagram** and skips when `UDPOverride.Port == 53`; domain resolution happens through the **built-in DNS module only when a strategy is set**, otherwise `net.DefaultResolver` (OS resolver). |
| XTLS/Xray-core `transport/internet/dialer.go` | Source | `sockopt.domainStrategy` resolution happens before `sockopt.dialerProxy` redirect — the innermost dialing hop's sockopt controls the real socket. |
| XTLS/Xray-core `app/dns/nameserver_doh.go`, `app/dns/hosts.go`, `infra/conf/dns.go` | Source | A remote DoH server is dialed through the dispatcher (so it can stay on the Freedom chain) but its **hostname must already resolve**; `dns.hosts` pins (`"host": ["ip", ...]`) are supported and break that bootstrap recursion. |

## 2. The defects found in Marble Freedom

### DEFECT A — the Freeedom chain never resolved destination hostnames (primary)

`XrayConfigHardener.harden()` applies the address-family plan only to outbounds that have
**endpoint domains** (`endpointDomains()`). The Freedom fragment hops have no endpoint — their
destination is whatever the user visits — so they kept the Xray default `AsIs`.

Xray's `freedom.go` behaviour with `AsIs` + a domain destination: **no built-in DNS
resolution — the domain is handed to the OS resolver** (`net.DefaultResolver` or
`net.ResolveUDPAddr`). In Iran that resolver is the poisoned ISP DNS (`10.10.34.0/24` block
pages, documented in `IRAN_MODE.md`) or silently drops the query. Every connection that reaches
the chain with a `domain` ATYP therefore fails:

- local-proxy (SOCKS) users — any browser/Telegram pointed at `127.0.0.1:<port>`;
- Marble's own in-tunnel SOCKS work: subscription refresh while connected, Privacy/exit-identity
  trace (`www.cloudflare.com`), live RTT probes (`www.gstatic.com`, `cp.cloudflare.com`,
  `www.google.com`), adaptive-DNS observation.

Both known-good upstream configs set it explicitly:

- XTLS: `sockopt.domainStrategy: "ForceIP"` + `happyEyeballs { tryDelayMs: 300, ... }` on
  `_chain-skip` / `full-fragment` (the hops that dial real sockets);
- GFW-knocker: `domainStrategy: "UseIP"` top-level **and** in `sockopt`.

**Fix:** `XrayConfigHardener` now applies `AddressFamilyPolicy.plan()` to the **innermost**
Freedom/direct fragment hop (the one with no `proxySettings`/`dialerProxy`) and writes it to
both `settings.domainStrategy` (used by the hop's UDP `PacketWriter`) and
`sockopt.domainStrategy` + armed Happy Eyeballs (used by the TCP dialer). TCP and UDP now both
resolve through Xray's encrypted DNS module — the same module whose DoH servers ride the
Freedom chain — instead of the poisoned OS resolver.

### DEFECT B — Freedom DNS servers that cannot bootstrap themselves

The Freedom DNS list (`freedomDnsCleanResolvers`) contained hostname DoH endpoints
(`dns.adguard-dns.com`, `doh.sb`, `dns.shecan.ir`) that the DNS module must resolve **before**
it can use them. With `useSystemHosts: false` and no `dns.hosts` pin, that resolution recurses
into the same module or falls out to the OS resolver — either way the server is unusable (or the
lookup is poisoned), while `finalQuery: true` sat on the last (Shecan) entry, making the
"guaranteed final answer" the least reliable one.

**Fix:** only IP-literal DoH endpoints or hosts pinned in `dns.hosts` survive; `dns.hosts`
pins `dns.shecan.ir` (178.22.122.100 / 185.51.200.2) and `dns.adguard-dns.com`
(94.140.14.14 / 94.140.15.15) — the same pattern GFW-knocker uses for `cloudflare-dns.com`.
`doh.sb` is removed from the defaults (no stable pin). TLS SNI still carries the real hostname,
so the DoH handshake stays authentic.

Note: `freedomDnsQueryStrategy` remains intentionally governed by `AddressFamilyPolicy` (one
decision for DNS records, endpoint dials, and blocking rules); a per-Freedom override that
disagrees with the IPv6 switch would be rejected by the hardener's invariant checks, so it
stays out of the emitted config rather than being silently half-applied.

### DEFECT C — DoH query budget too small for a fragmented first write

The first TCP write of every stream on the chain is sliced into 1-byte packets with 4 ms
pacing (up to `maxSplit` 517), so a DoH TLS ClientHello alone can take ~1–2 s. The Freedom DNS
servers inherited the generic 1 350 / 1 650 ms serial budgets — i.e. every server looked dead
even when reachable, and cold lookups could burn 8+ s before a working resolver was found
(if any). XTLS's official config gives its DoH server **10 000 ms** for precisely this reason.

**Fix:** Freedom DoH entries now get 5 000 ms + 750 ms per position (bounded, serial as before);
`serveStale` still returns validated answers while background refresh runs.

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

## 3. What was checked and confirmed working

- MarbleNG already accepts a freedom/fragment-only outbound graph
  (`XrayConfigHardener.isSelectableProxy`, `infra` handling) — the v2rayN/v2rayNG
  "fake VLESS outbound required" bug is not present.
- The emitted `fragment`/`noises` shapes (`packets`, `length`, `interval`, `maxSplit`,
  `type: rand`, `packet`, `delay`, `applyTo`) match what Xray `infra/conf/freedom.go` parses at
  v25.12.8 and v26.7.28.
- The `dns`-outbound `rules` (`hijack`/`return`, `qType 1,28`) match the v26
  `DNSOutboundConfig` schema (legacy `nonIPQuery`/`blockTypes` is deprecated).
- `dns.hosts` accepts `"host": ["ip1", "ip2"]` (`HostAddress.UnmarshalJSON`, `newHostMapping`).
- `fragment` applies to TCP in `freedom.go`; `noises` to the first UDP datagram only — the
  same split the official config relies on.
- `sockopt.dialerProxy` chains bridge the original destination through every hop, so the
  innermost hop sees the user destination (and `redirect()` carries UDP with per-packet
  destinations via `cnc.ConnectionOutputMultiUDP`).

## 4. Next verification (needs the runtime log)

The app log the user referenced was **not present** in this workspace when the work started
(only the repository checkout). The fixes above target the failure modes that match the
reference configs and Xray source, but the last mile still needs the log:

- Bug Finder → Debug Mode export (or `adb logcat | grep -iE "xray|mart|hev|dns"`) while
  reproducing Freedom;
- or the generated runtime config at `context.cacheDir` so `xray run -test -c <file>` output
  can be compared line-by-line with `Serverless-for-Iran/serverless_for_Iran.jsonc`.

## 5. Git/log evidence left in place

The changed files:

- `app/.../core/XrayConfigHardener.kt` — DEFECTS A–C.
- `app/.../vpn/MarbleVpnService.kt` — DEFECT D.
- `app/.../model/Models.kt` — DEFECT B defaults.
- `app/.../core/XrayConfigHardenerTest.kt` (+ `org.json` test dependency) — regression tests.
