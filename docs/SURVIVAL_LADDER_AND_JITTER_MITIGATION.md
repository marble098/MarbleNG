# Architectural Overhaul: Survival Testing, Monotonic Ladder, Hierarchical Thompson Sampling, and Real Jitter Mitigation

*Core modules:* `TransportAdaptation.kt`, `HighJitterShield.kt`, `SocksHttpClient.kt`, `RouteProbe.kt`, `CoreSocketPolicy.kt`, `MarbleVpnService.kt`, `MarbleHomeStyles.kt`

---

## 1. The Core Critiques & Root Causes Addressed

1. **Ping Deception Under Censorship**
   - *Problem:* In filtered and DPI-heavy networks (e.g. Iran), stateful middleboxes frequently inject fake SYN-ACK or permit initial TLS handshakes, only to inject RST packets or silently drop traffic once ~10–20 KB is transferred. Flat RTT measurements (SYN/1-byte read) yield falsely optimistic latency figures for dead or poisoned routes.
   - *Solution:* Implemented `verifySurvivalAndTransfer` in `SocksHttpClient` and `verifyRouteSurvival` in `RouteProbe`. Probes must complete TLS handshake, record TTFB, and survive data transfers of $\ge 20$ KB or sustain active streams for $\ge 15$ seconds without injected RST or connection drop.

2. **Jitter Confounded with Cellular Radio Sleep (LTE RRC Transition)**
   - *Problem:* Cellular radios in LTE/5G drop into RRC IDLE / DRX sleep after brief inactivity (5–10s). The initial packet transmitted after an idle gap incurs an RRC Connection Setup delay of 100–1000 ms. When this packet is fed into steady-state jitter estimators, it produces synthetic spikes, triggering excessive probe bursts and battery drain.
   - *Solution:* Introduced idle-gap tagging (`RRC_IDLE_GAP_MS = 6_000L`, `isRadioWakeup: Boolean`). The shield isolates and labels radio-wakeup latency, excluding RRC transition delays from steady-state channel IQR dispersion calculations and preventing run-away probe bursts.

3. **Multi-Arm Bandit Sparsity & Arbitrary Constants**
   - *Problem:* 48 unstructured arms (8 fragment profiles $\times$ 6 mux profiles) combined with sparse operator and time-of-day bins meant the bandit suffered acute cold-start penalties and virtually never converged in practice.
   - *Solution:*
     - **Monotonic Fragment Ladder:** Ordered from mildest (OFF) to most aggressive (EXTREME). Because DPI bypass is monotonic (if a milder setting survives, heavier settings only add useless overhead), we perform a binary or progressive walk to find the *minimal effective setting*.
     - **Hierarchical Thompson Sampling:** Hierarchical Beta distributions: Global Prior $\to$ Operator/ASN $\to$ Cell (Operator $\times$ NetworkType $\times$ DayPart). Sparse cells smoothly back off to parent priors.
     - **Real Reward Function:** Evaluates handshake + survival ($\ge 15$s or $\ge 20$KB) + TTFB, and subtracts the fragment setting overhead (`overheadPenalty`).
     - **Safe Exploration:** Live traffic uses the proven/incumbent ladder setting; exploratory evaluation is restricted to isolated test sockets with a strict probe budget.
     - **Page-Hinkley Change-Point Detection:** Detects abrupt shifts in operator DPI rules and triggers targeted ladder re-evaluation.

4. **Real Jitter Reduction: HOL Blocking, Bufferbloat, and Android Latency**
   - *Problem:* Probing alone cannot fix jitter. TCP multiplexing over a single connection suffers severe Head-Of-Line (HOL) blocking when packets are dropped on lossy links. Unchecked socket buffers induce massive bufferbloat. Wi-Fi power-saving modes add 50–200 ms latency spikes.
   - *Solution:*
     - **Mux Pool:** Replaced single-connection mux bottlenecks with multi-connection mux pools (`maxConnections: 2..4`) and automatic mux demotion/disablement under elevated loss/jitter.
     - **Bufferbloat Mitigation:** Integrated `TCP_NOTSENT_LOWAT` (`tcpNotSentLowat = 16384`) in physical socket configurations to limit queued in-flight data.
     - **Android Low-Latency WifiLock:** Acquired `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` on Android 10+ (API 29+) while the VPN tunnel is active on Wi-Fi.
     - **Lightweight Keepalive:** Activated traffic-aware keepalive during active sessions to keep cellular basebands in high-speed states without wasting battery when idle.

5. **Visual Refinement: Clean, Modern Ping Button**
   - *Problem:* The Home header's pulsing ECG heartbeat animation was visually distracting and tied to arbitrary 160 ms thresholds.
   - *Solution:* Replaced the heartbeat canvas animation with `HomeModernPingAction`: a sleek, modern pill button featuring `HomeGlyph.PULSE`, crisp latency readout (`$pingMs ms`), clear cancellation handling (`HomeGlyph.STOP`), and tactile spring feedback.
