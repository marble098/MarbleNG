# V238 — Smart Jitter Reducer & Link Remediation

MARBLE_SMART_JITTER_V238 · MARBLE_NOTIFICATION_PICKER_V238 · MARBLE_TCP_INFO_TRUTH_V238 ·
MARBLE_PROBE_TARGET_WINDOWS_V238 · MARBLE_DNS_DEADLINE_V238 · MARBLE_FAILOVER_TRUST_V238 ·
MARBLE_PRIVATE_DNS_BLOCK_V238 · MARBLE_NTP_DIRECT_V238 · MARBLE_MTU_CEILING_V238

The Bug-Finder v11.2.3 pass (VpnService + Xray + tun2socks/hev, sing-box secondary) plus the
Smart Jitter Reducer module and the notification route picker. Nothing in the report's PASS
sections was touched, and the two findings the report itself marks as environmental
(`packages.xml` warning, `killDueToPackageUpdate`) and the Force-IPv6 gate (kept; only the
resolver *strategy* reacts to a v6-less underlay) were deliberately left alone.

## The Smart Jitter Reducer (`app/src/main/java/com/marbleng/app/jitter/`)

A new, opt-in module — **default OFF; OFF is the pre-module behaviour byte for byte**. When the
user enables it (Balanced / Aggressive on the new Settings → Smart Jitter Reducer page), a
coroutine on `DispatcherProvider.io` probes the *live* route through its own SOCKS channel with a
light HTTPS request (never ICMP, never user data, endpoint configurable), scores the path, and
switches only on clear, repeated evidence.

| Piece | Rule |
| --- | --- |
| `JitterWindow` | ring of 30; **no verdict below 8 valid samples**; a cold (fresh-connection) sample counts as an attempt but is excluded from RTT/mean/p95 and breaks the RFC 3550 jitter pairing chain |
| `ScoreEngine` | 0–100 score: jitter .35 / p95 .25 / RTT mean .25 / loss .15 (references 25 ms / 160 ms); mux-capable paths are penalized ×0.9 at ≥40 ms jitter (head-of-line blocking) |
| `SwitchPolicy` | challenger must beat the incumbent by ≥20%, ≥180 s since the last switch, ≥90 s dwell, and a dead incumbent still demands an absolute challenger score ≥40 |
| `ProbeScheduler` | 5 s base ×1.5ᶜᵃˡᵐ (streak ≤ 6), capped at 60 s, ×2 in battery saver / ×3 in Doze |
| `SmartJitterController` | single wake-based loop, cancel-safe `close()`, `updateTuning` relaunches the loop after an OFF→active transition, switch requests travel to the service as a 4 s TTL request |

## Notification route picker (Task A)

`MARBLE_NOTIFICATION_SWITCH_V234`'s verbs (next server / next subscription / reconnect) stay.
The gap — *pick a specific route from the shade* — is `MARBLE_NOTIFICATION_PICKER_V238`:
`core/NotificationRoutePicker.kt` (pure), live-subscription server rows + sibling-subscription
rows + an "open app" row, capped at 9 rows, plus `ACTION_SELECT_ROUTE` on the service
(`selectRouteFromShade`: session guard → the shared 1.5 s throttle → connect). The live route is
marked in the picker state, and the layout ships RTL twins (`notification_picker_row_rtl.xml`).

## Report remediation map

| Report item | Fix |
| --- | --- |
| P0-1 `jitterPressure` false positives | `marble_telemetry_linux.go` tracks loopback dials and only established, non-loopback sockets with a kernel RTT join the statistics (`ValidSockets` field); the policy refuses to read RTT columns with zero valid sockets and a `PressureGate` demands **two consecutive** pressured snapshots before `stressed` drives anything. Go test: `marble_telemetry_linux_test.go`. |
| P0-2 cold-sample verdicts | `LinkQualityEstimator.summarizeMarked` (cold mask), per-target `RouteSampleWindows` (12 samples × 4 hosts × 10 min TTL, returning to a target restores its window), the first sample of every burst is marked cold, and `AdaptiveProbeCadence` stretches the legacy probe on calm evidence. |
| P0-3 measured=0 scored as success | both scorers hard-return on `measured <= 0`; the home-ping diagnostic line now names the failure reason. |
| P0-4 stale BugFinder marker | `BugFinder` reads the ring with the tag the probe writes today (the retired spellings count as history). |
| P1-5 DoH deadline storm | floors raised to 4 000 / 4 500 ms, thin evidence (<5 samples) never scales above the floor, DoH retries keep the connection alive (`Connection: keep-alive` on every attempt), demotion probation shortened to 15 min, Private DNS (DoT to tcp:198.18.0.2:853) is rejected at the TUN inbound so Android falls back to the hijacked resolver in seconds, `type65` answered empty and AAAA answered NODATA when the underlay lacks IPv6 (Force-IPv6 itself untouched). |
| P1-6 connect/switch churn | 700 ms same-profile start debounce; an 8 s `waitForUsableUnderlay` gate before a core start; failover switches need a 90 s cooldown **and** ≥0.5 evidence confidence (`FailoverTrustPolicy`, pure + tested); no-underlay probes are skipped without penalty. |
| P1-7 stale fake-IP | Xray + armed DNS path ⇒ the TUN is rebuilt on route switch (the FakeDNS map dies with the core); sing-box ⇒ the TUN is preserved (`cache_file` + `store_fakeip` persist the map). |
| P1-7b MTU | `adaptiveMtu` is capped by `ProtocolMtuCeiling.forScheme(scheme, transport)`; the tuner's override (off by default, recommend-only) sits under the same ceiling. |
| P2-8 NTP over UDP/123 | real-address NTP rides the **direct** outbound (Xray hardener + sing-box builder) so the reply's source address matches the socket that asked; domain-resolved NTP still follows the proxy via the FakeDNS rule that sits earlier. |
| P2-9 memory | the sing-box session stays lazy (unchanged); tcp-info and session logs stay capped to the session file (unchanged). |

## Next-log acceptance criteria

1. No `jitterPressure` verdict may originate from loopback or `rtt=0` sockets — the tcp-info line
   carries `validSockets`, and a `validSockets=0` line cannot produce pressure.
2. No jitter-control verdict/switch below 8 valid samples (window + score both gate).
3. No resolver demotion storm: the first DoH query of a cold session budgets ≥4 000 ms, thin
   evidence cannot raise a deadline above the floor, and `Connection: close` no longer appears on
   DoH retries.

## Explicit limitations

- The Smart Jitter Reducer is **recommend + switch only**; it never rewrites routing rules and it
  never touches user data — the probe is a 0-byte HTTPS request through the tunnel.
- MSS clamping is left to the cores' existing outbound socket options; V238 only caps the TUN MTU
  by protocol overhead.
- The hev symmetric-NAT NTP path is avoided by routing, not fixed inside hev.
- The Private DNS block is scoped to the TUN's own inbound; intentional remote DoT through the
  proxy is untouched.
- `SmartJitterController` needs kotlinx-coroutines at compile time (already on the classpath via
  `lifecycle-runtime-ktx`); its loop is only exercised on-device.
