# The IPv6/memory/socket triangle: a connected core delivering nothing (V135)

Runtime evidence analysed: a session that reconnected six times in under two minutes
(20:57, 20:58:56, 20:59:04, 20:59:07, 20:59:15, 20:59:21), in which `home-connection-ping`
reported `measured=4/5 ms` beside first-byte readings of 242–347 ms, DNS lookups died with
`context deadline exceeded` while the retained Xray tail showed accepted connections to
`tcp:[2606:4700:4700::1111]:853` and `udp:[2001:4860:4860::8888]:443`, PSS climbed to
~318 MB (`pssKb=318762`) under repeated `MEMORY | trim level=20/40`, and the OS finally
revoked the VPN permission (`binder:8946_1 | VPN | permission-revoked`).

The `measured=4` figure deserves one explicit sentence because it reads like a contradiction:
it is the round-trip time to the LOCAL SOCKS inbound on the device (`127.0.0.1`), not to the
server. The honest route latency is the first-byte ladder (242–347 ms). The server was slow to
reach, never fast — and yet nothing reached it, because three structural defects locked
together into a triangle: an IPv6 dead end, an unbounded reconnect loop, and the memory cost
of both.

---

## 1. The IPv6 dead end (the primary fault)

The network had **no global IPv6** — the `[WARN] IPv6 path` line said so — and still the
session carried IPv6 DNS endpoints:

* the TUN DNS server list admitted v6 resolvers on ANY network; the bootstrap rule only ever
  asked "did the user configure an IPv6 literal", never "can this underlay dial it";
* the hardener gated the *stock* v6 bootstrap literals on `underlayHasIpv6` but NOT the
  user-configured `dnsPrimaryIp` / `dnsSecondaryIp`, which is where the unreachable entries
  came from;
* the TUN captured `::/0` unconditionally (`ipv6Captured=true`). `Builder.addRoute("::", 0)`
  succeeds on almost every device regardless of what the physical network can route, so the
  capture looked like protection while behaving like a blackhole: Android Private DNS dialling
  `[2606:4700:4700::1111]:853` and every AAAA-derived destination were swallowed by the tunnel
  and died on an unreachable path while applications waited.

`https+local://` bootstrap resolvers dial the UNDERLAY directly. An IPv6 literal there on an
IPv4-only network is not a fallback — it is a guaranteed deadline. With serial failover every
cold lookup paid the whole `timeoutMs` of each dead resolver in turn before reaching one that
could answer, and on the fragment chain that budget was 8 s per resolver (see §3). No DNS
answer means no destination IP, which is exactly what surfaces one layer up as
`unexpected EOF` on the user's sockets.

**Fix** — one verdict, applied everywhere (`MARBLE_IPV6_DNS_PURGE_V135`):

* `XrayConfigHardener.harden()` now takes the underlay verdict as a parameter (one probe per
  build, deterministic in tests) and purges IPv6 literals from the user-configured bootstrap
  resolvers, the stock bootstrap ladder and the Freedom resolver list whenever the underlay
  cannot carry IPv6 — or whenever the user demanded IPv4-only. The Freedom chain is included
  because it dials its resolvers directly on the underlay; the rank config
  (`hardenForNativeRank`) applies the same gate, which it previously accepted as a parameter
  but never used;
* a dual-stack underlay now keeps its family-diversity promise: one stock v6 literal is placed
  INSIDE the six-entry bootstrap budget instead of appended after it, where `take(6)` truncated
  it whenever the user had configured any resolver at all;
* `MarbleVpnService.runTun()` admits a TUN DNS server only when its family matches the
  underlay, never seeds v6 bootstrap resolvers on an IPv4-only network, and picks the
  never-empty fallback literals per family;
* the `::/0` route capture itself is now gated: the snapshot's link properties OR the direct
  interface probe may prove IPv6, and only when NEITHER sees a routable global address does the
  capture stay off (`ipv6-capture-skipped`). Nothing to carry means nothing to leak, so the
  Identity Guard scope is unchanged — it already treats exactly that case as safe.

## 2. The unbounded reconnect loop

Smart Fallback advanced to the next candidate with **zero delay**, and nothing counted
failures inside a time window, so a failure cause that affects every candidate identically
(a dead DNS path — §1) burned six full HEV/Xray lifecycles in 113 seconds. Each cycle paid a
core start, a TLS bring-up and a teardown: CPU heat, fresh native session buffers, battery —
the workload became the recovery machinery.

**Fix** — `core/RecoveryBackoffPolicy.kt` (`MARBLE_RECOVERY_CIRCUIT_V135`), pure and
side-effect free like the other policies: an exponential ladder (1.5 s → 30 s) paces every
automatic recovery, and a rolling five-minute circuit opens after six automatic recoveries —
recovery then holds fail-closed (Full TUN keeps blocking traffic) and the decision goes back
to the user. The Identity Guard same-route retries share the ladder: their legacy 750 ms ramp
is now a floor, so the two paths cannot combine into one storm. A route that publishes
readiness resets the ladder; a fresh user-initiated connect starts it empty.

## 3. Deadlines that ignored the link on the fragment chain

V133 made the tunnel-routed DNS budgets a function of the measured link but deliberately kept
the Freedom fragment chain on the upstream XTLS schedule (8 s / 9 s / 10 s), because the
1-byte/4 ms first-write pacing once dominated a 1.1 s route. On the 242–347 ms route in this
log that same constant meant a dead resolver held serial failover for 8 s before the next one
was tried — the exact `context deadline exceeded` stalls above.

**Fix** — `LinkDeadlinePolicy` (`MARBLE_FRAGMENT_DEADLINE_V135`): on a MEASURED link the
fragment budget is now `pacing overhead (≈2.1 s for 517×4 ms) + 3 × tail-RTT + jitter/loss
headroom`, floored at 3 s and capped at the 10 s XTLS reference. An unmeasured link keeps the
legacy schedule bit for bit, because there the pacing, not the RTT, dominates.

## 4. Memory pressure and the revoked VPN permission

318 MB PSS with trim levels 20/40 arriving repeatedly is what an OS looks like right before it
kills a VPN service; `permission-revoked` is the receipt. Most of the footprint is the native
core and the recovery storm (§2), but the Kotlin-side trim ladder released too little too
late. **Fix** (`MARBLE_MEMORY_TRIM_V135`): reconstructable state is released one step earlier
(privacy report at critical, bug report + server-intel cache at 20, benchmarks at 40), and the
trim event now reports the Java heap used so the next log shows whether the trim freed memory.

## 5. The Home surface: the live ping box that moved the page

Separate from the network plane, users reported that tapping Connect made the live ping
instrument suddenly appear and take layout space, pushing the status headline and every
readout below it down the page. The instrument was an `AnimatedVisibility` — entering the
layout is exactly what moved everything. **Fix** (`MARBLE_LIVE_PING_FIXED_SLOT_V135`): the
meter occupies a PERMANENT slot in every Home presentation; it is composed in every connection
state, and the CONNECTED state (never the CONNECTING ramp) only animates its opacity inside
that slot. Its chrome now complements the connect control — the same elevated glass, washed
and hairlined with the control's own state tone. Nothing around it can move because nothing
around it changes size.

---

### Why this client failed while other clients on the same server worked

The other clients either ran on a network that carries IPv6, or never handed their DNS module an
IPv6 endpoint, or paced their reconnects. Marble did all three wrong at once on this network:
it emitted an unreachable resolver graph, captured a route the underlay could not carry, paid
8 s per dead resolver, and re-dialled with no pacing until the OS intervened. Each fix above
removes one side of that triangle permanently; the circuit breaker guarantees that any future
structural fault degrades into a held, explained pause instead of a storm.
