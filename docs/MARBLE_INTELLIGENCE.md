# Marble Intelligence Engine v4.1

Marble Intelligence Engine turns MarbleNG's existing Xray + HEV VPN pipeline into an adaptive,
network-aware control plane while keeping Full TUN fail-closed behavior as the security invariant.

## What v4 implements

- Network-scoped persistent node health history in a bounded SQLite database.
- Predictive Smart Route candidate ordering using historical health on the current network class.
- Small real-Xray connection race for fast time-to-first-working-route.
- Bounded same-TUN fallback: Full TUN stays established/blackholed while Xray + HEV are replaced.
- ConnectivityManager network sensing and fast re-probe after Wi-Fi/cellular/link-property changes.
- Conservative adaptive MTU selection with IPv6-safe floor instead of a permanent jumbo MTU.
- Xray DNS-out interception for TCP/UDP :53, hijacking A/AAAA into Xray built-in encrypted DNS.
- Adaptive IPv4/IPv6 query strategy and DoH preference based on current link capabilities.
- Benchmark validation caching independent of ephemeral benchmark ports.
- Thermal-aware worker/sample/byte budgets.
- Adaptive throughput probing with a bounded larger sample only when useful.
- Real SOCKS5 UDP ASSOCIATE + STUN health probe.
- Workload-aware Interactive / Streaming / Stability / Resilience scores.
- Adaptive Fragment retry only after a normal path fails, with network-scoped learned preference.
- Adaptive Mux experiment on eligible stable/high-RTT TCP routes rather than globally forcing Mux.
- Privacy Sentinel state separating device-wide Full TUN from intentionally partial Local Proxy / split bypass.
- Honest KernelSU/eBPF and dual-network capability reporting. v4 does not claim a root eBPF datapath or
  bandwidth aggregation because MarbleNG does not implement those data planes.

## Security invariants

1. Full TUN is established before Xray startup.
2. Device IPv4 and IPv6 are captured by the VPN interface.
3. On forwarding failure the Full TUN interface remains established, so captured traffic is blackholed.
4. Ordinary unmatched traffic remains proxy-first/fail-closed.
5. Traditional DNS :53 can be routed to Xray dns-out; A/AAAA are handled by built-in encrypted DNS.
6. The app/Xray UID remains outside its own TUN to prevent a routing loop.
7. Local Proxy mode is explicitly partial coverage and does not pretend to provide a device kill switch.

## Intelligence model

Health is keyed by profile ID + privacy-safe network fingerprint. The fingerprint contains only broad
network properties (transport, IPv4/IPv6 availability, metered state, MTU and coarse bandwidth buckets),
not SSID, IMSI, phone number or location identifiers.

The database stores bounded EWMA health rather than raw browsing/activity history:
- success
- latency
- jitter
- throughput
- UDP success
- connection time
- failure streak
- learned Fragment/Mux preference
- last successful/seen timestamps

## Benchmark philosophy

TCP precheck is only a dead-node gate. Final ranking comes from real temporary Xray tunnels plus history.
Benchmark runtime uses proxy-all policy so custom direct/geo/ad-block rules cannot make a proxy appear fast
by bypassing the test.

## Marble Turbo — measured connection acceleration

Prediction and node comparison cannot help a user who has exactly one configuration that they want to
use. Marble Turbo improves *that* node instead of replacing it: when the user connects, the engine
executes real transport methods against the selected configuration and keeps the one that measures
fastest.

Executed methods (only the ones that are physically meaningful for the node are tried):

| Method | What it changes | When it is offered |
| --- | --- | --- |
| `direct` | nothing — the user's own configuration | always, as the reference |
| `fragment-tlshello` | `Freedom.fragment` `tlshello / 100-200 / 10-20` on the server dial | TLS/REALITY nodes |
| `fragment-short` | `Freedom.fragment` `1-3 / 40-90 / 5-10` | TLS/REALITY nodes |
| `mux-reuse` | Mux `concurrency 8`, `xudpConcurrency 16` | VLESS/VMess/Trojan/SS, non-UDP-native |
| `mux-light` | Mux `concurrency 4`, `xudpConcurrency 8` | VLESS/VMess/Trojan/SS, non-UDP-native |
| `dns-v4` | endpoint resolution forced to `UseIPv4` | dual-stack links only |

Rules that keep this honest:

1. Every method is measured through a **throwaway Xray instance** on the real link — HTTPS latency for
   every pass, plus a bounded download for background passes. Nothing is inferred from a config file.
2. A method only ever **adds** transport behaviour. The user's explicit Fragment/Mux choices and Iran
   Mode countermeasures are never switched off by acceleration.
3. A winner must beat the untouched baseline by a material margin on the axis it claims (≥12% latency
   or ≥22% throughput) and may not regress the other axis. Otherwise the baseline is kept.
4. The exit node never changes, so acceleration is safe while **Identity Guard** has the public IP
   pinned — the one situation where Autopilot is deliberately disabled.
5. The winner is stored per profile + network fingerprint with a 6 hour freshness window, so the
   measured connect happens once per node per network and later connections are instant.

While connected, a background pass re-measures the live route (once opportunistically, then whenever
median ping crosses the configured trigger, or immediately on user request). If a different method is
materially faster than the one currently running, Xray is restarted on the same node with the better
method while Full TUN stays fail-closed. Restarts are capped per session so a noisy link cannot cause
flapping.

## Adaptive tunnel datapath

Userspace tunnel buffers and session limits are derived from measured throughput (acceleration
evidence first, long-run EWMA history second, link capability last) instead of one fixed compromise:
a fast link is no longer capped by a 64 KiB socket buffer, and a thermally throttled device is never
given buffers it cannot fill.

## Recovery philosophy

MarbleNG uses hysteresis and a bounded fallback set instead of constantly hopping nodes. When a Full TUN
route degrades repeatedly, forwarding is stopped, the existing Android VPN interface remains alive, and a
historically strong alternate node is started on the same captured TUN path. If recovery cannot establish a
healthy route, the client remains fail-closed rather than silently falling back to the physical network.

