# MarbleNG Network Extreme v51

This change set separates measurable production improvements from native experiments.

## Active in this change

- Android 11+ ConnectivityDiagnostics data-stall callbacks.
- Data-stall debounce, confirmation window and cooldown.
- Shared rolling RTT, IPDV, loss, p90 and Wilson lower-confidence estimator.
- Fair Rank target rotation: every node in one network/time epoch uses the same origin order.
- Transport-aware root-free MTU ceiling.
- Conservative Xray TCP keepalive and TCP user-timeout values.
- JVM tests for statistics, MTU, Rank fairness, stall control, DNS hedging and handover rules.
- Candidate-branch compile/test gate before an atomic main fast-forward.

## Compiled decision primitives, not falsely advertised as active datapaths

- DnsHedgePolicy defines bounded delayed-secondary behavior for a future full wire-format
  DoH/DoQ proxy. Xray's built-in DNS outbound currently handles A/AAAA; MarbleNG must not return
  fabricated success for unsupported HTTPS/SVCB/SRV/TXT/DNSSEC queries.
- HandoverCoordinator defines verify, switch-new-flows and drain-old ordering. Actual flow
  draining requires a resident Xray instance with multiple outbounds or an embedded core.

## Native gates still required

1. **Network-bound dialer bridge:** Xray outer FDs must be protected by VpnService.protect(fd)
   and bound with Network.bindSocket(fd) before connect. The current Xray executable has no FD
   callback into Android, so this requires an embedded core or an authenticated Unix FD bridge.
2. **FQ-CoDel/pacing:** implement per-flow queues inside HEV/native forwarding and validate loaded
   latency on physical devices. A Kotlin placeholder cannot control HEV's packet scheduling.
3. **DPLPMTUD:** feed real black-hole/retransmission evidence from native TCP/QUIC into the MTU
   policy and apply the learned value on the next VPN establishment.
4. **Passive TCP/QUIC telemetry:** expose TCP_INFO and transport loss/PTO counters from the process
   that owns the outer sockets.
5. **Resident Rank and make-before-break:** reuse one core with tagged outbounds, verify challenger,
   switch only new flows and drain old flows without changing exit identity when Identity Guard is
   enabled.

No item in the native-gate section is claimed as complete merely because its policy model compiles.
