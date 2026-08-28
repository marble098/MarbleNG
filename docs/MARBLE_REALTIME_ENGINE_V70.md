# Marble Realtime Engine v70
`MARBLE_REALTIME_ENGINE_V70`

## Active
- Robust median/p90/p95 RTT, median/p95 IPDV, EWMA jitter, MAD, loss and spike frequency.
- Shared jitter-first quality score for Rank, PattRank, Turbo and Autopilot.
- Adaptive Happy Eyeballs/address racing instead of a fixed 180 ms delay.
- Measured TCP Fast Open and PMTU-derived MSS Turbo candidates.
- Live-Xray Linux TCP_INFO telemetry and network/profile-scoped passive PMTU learning.
- HEV queue-pressure/buffer reduction when measured jitter is high.
- Candidate Android + injected-Xray CI before main can move.

## Native gates — intentionally not faked
1. **FQ-CoDel/pacing inside HEV** still requires a maintained HEV scheduler/fork or resident shim.
2. **QUIC/Hysteria PTO/cwnd telemetry** needs hooks in the QUIC owner; TCP_INFO is TCP-only.
3. **True resident make-before-break flow draining** needs a resident multi-outbound core/relay; the current fixed HEV SOCKS topology cannot keep old flows on A and new flows on B.
4. **VpnService.protect + Network.bindSocket FD bridge** needs an authenticated FD callback/embedded core.

The active HEV buffer guard is not advertised as FQ-CoDel, and the passive PMTU cache is not advertised as full RFC 8899 DPLPMTUD.
